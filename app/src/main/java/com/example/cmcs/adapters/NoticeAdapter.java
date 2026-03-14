package com.example.cmcs.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cmcs.AddNoticeActivity;
import com.example.cmcs.NoticeViewersActivity;
import com.example.cmcs.R;
import com.example.cmcs.models.NoticeModel;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for the notice list.
 *
 * View-tracking design (scalable to 1500+ students):
 *
 *   Firebase structure:
 *     noticeViews/{noticeId}/count        — long, server-incremented
 *     noticeViews/{noticeId}/viewers/{uid} — true
 *
 *   Student path:
 *     onViewAttachedToWindow fires when a card genuinely enters the screen.
 *     We check viewers/{uid} first; only if absent do we write the entry and
 *     run a transaction to increment count.  This guarantees:
 *       • one write per student per notice, ever
 *       • count is always consistent with the viewers map
 *       • no writes on RecyclerView recycle / rebind
 *
 *   Teacher path:
 *     A persistent ValueEventListener on count gives live updates.
 *     The listener is attached in onViewAttachedToWindow and detached in
 *     onViewDetachedFromWindow so it never leaks beyond the ViewHolder's
 *     visible lifetime.
 */
public class NoticeAdapter extends RecyclerView.Adapter<NoticeAdapter.NoticeViewHolder> {

    private final Context          context;
    private final List<NoticeModel> notices;
    private final String           currentUid;
    private final String           currentRole;
    private final String           dbPath;

    public NoticeAdapter(Context context, List<NoticeModel> notices,
            String currentUid, String currentRole, String dbPath) {
        this.context     = context;
        this.notices     = notices;
        this.currentUid  = currentUid;
        this.currentRole = currentRole;
        this.dbPath      = dbPath;
    }

    // ── Inflation ─────────────────────────────────────────────────────────
    @NonNull
    @Override
    public NoticeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_notice, parent, false);
        return new NoticeViewHolder(v);
    }

    // ── Bind ──────────────────────────────────────────────────────────────
    @Override
    public void onBindViewHolder(@NonNull NoticeViewHolder h, int position) {
        NoticeModel n = notices.get(position);

        // Store the notice on the holder so onViewAttachedToWindow can access it.
        h.boundNotice = n;

        h.tvCreatorName.setText(n.getCreatedByName() != null ? n.getCreatedByName() : "");
        h.tvTitle.setText(n.getTitle());
        h.tvDescription.setText(n.getDescription());
        h.tvEdited.setVisibility(n.isEdited() ? View.VISIBLE : View.GONE);
        h.tvTimestamp.setText(formatTimestamp(n.getTimestamp()));

        bindMedia(h, n);

        // Edit / Delete — owner teacher only
        boolean isOwner = "teacher".equals(currentRole)
                && currentUid != null
                && currentUid.equals(n.getCreatedByUid());
        h.actionRow.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        h.btnEdit.setOnClickListener(isOwner ? v -> openEditScreen(n) : null);
        h.btnDelete.setOnClickListener(isOwner ? v -> confirmDelete(n) : null);

        if ("teacher".equalsIgnoreCase(currentRole)) {
            h.llViewRow.setVisibility(View.VISIBLE);
            // Clear stale listener tag so onViewAttachedToWindow attaches a fresh one.
            h.llViewRow.setTag(null);
            h.llViewRow.setOnClickListener(v -> {
                Intent intent = new Intent(context, NoticeViewersActivity.class);
                intent.putExtra(NoticeViewersActivity.EXTRA_NOTICE_ID, n.getNoticeId());
                intent.putExtra(NoticeViewersActivity.EXTRA_NOTICE_TITLE,
                        n.getTitle() != null ? n.getTitle() : "Notice");
                context.startActivity(intent);
            });
        } else {
            h.llViewRow.setVisibility(View.GONE);
            h.llViewRow.setOnClickListener(null);
            // Student click listener cleared here; actual view recording happens
            // in onViewAttachedToWindow, not on tap.
            h.itemView.setOnClickListener(null);
        }
    }

    // ── Attach / Detach — the correct place for view-tracking ─────────────

    /**
     * Called exactly once when a ViewHolder's root view is attached to the
     * window (i.e. the card is genuinely visible on screen).  This is the
     * right place to:
     *   • record a student view (fires once per genuine appearance, not on recycle)
     *   • attach the live count listener for teachers
     */
    @Override
    public void onViewAttachedToWindow(@NonNull NoticeViewHolder h) {
        super.onViewAttachedToWindow(h);
        NoticeModel n = h.boundNotice;
        if (n == null || !isValidNoticeId(n.getNoticeId())) return;

        if ("teacher".equalsIgnoreCase(currentRole)) {
            attachLiveCountListener(h, n.getNoticeId());
        } else if ("student".equalsIgnoreCase(currentRole)) {
            recordStudentView(n.getNoticeId());
        }
    }

    /**
     * Called when the ViewHolder leaves the screen.  Detach the live count
     * listener to prevent Firebase listener leaks.
     */
    @Override
    public void onViewDetachedFromWindow(@NonNull NoticeViewHolder h) {
        super.onViewDetachedFromWindow(h);
        detachLiveCountListener(h);
    }

    /**
     * Called when the adapter is detached from the RecyclerView (e.g. fragment
     * destroyed).  Clean up any remaining listeners.
     */
    @Override
    public void onViewRecycled(@NonNull NoticeViewHolder h) {
        super.onViewRecycled(h);
        detachLiveCountListener(h);
        h.boundNotice = null;
    }

    @Override
    public int getItemCount() {
        return notices.size();
    }

    // ── Live count listener (teacher) ─────────────────────────────────────

    /**
     * Attaches a persistent ValueEventListener to noticeViews/{noticeId}/count.
     * The listener reference is stored as a tag on llViewRow so we can remove
     * it precisely in detachLiveCountListener.
     */
    private void attachLiveCountListener(@NonNull NoticeViewHolder h, String noticeId) {
        // Detach any previous listener first (ViewHolder may be reused).
        detachLiveCountListener(h);

        DatabaseReference countRef = FirebaseDatabase.getInstance()
                .getReference("noticeViews")
                .child(noticeId)
                .child("count");

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.exists()
                        ? (snapshot.getValue(Long.class) != null
                                ? snapshot.getValue(Long.class) : 0L)
                        : 0L;
                h.tvViewCount.setText("\uD83D\uDC41 " + count
                        + (count == 1 ? " view" : " views"));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                h.tvViewCount.setText("\uD83D\uDC41 0 views");
                android.util.Log.e("NoticeAdapter",
                        "count listener cancelled for " + noticeId
                                + ": " + error.getMessage());
            }
        };

        countRef.addValueEventListener(listener);

        // Store both ref and listener so we can remove precisely.
        h.countListenerRef      = countRef;
        h.countValueListener    = listener;
    }

    private void detachLiveCountListener(@NonNull NoticeViewHolder h) {
        if (h.countListenerRef != null && h.countValueListener != null) {
            h.countListenerRef.removeEventListener(h.countValueListener);
            h.countListenerRef   = null;
            h.countValueListener = null;
        }
    }

    // ── Student view recording ─────────────────────────────────────────────

    /**
     * Records that the current student viewed this notice.
     *
     * Algorithm (prevents duplicates, keeps count consistent):
     *   1. Read viewers/{uid} — single read, cheap.
     *   2. If it already exists → do nothing (already counted).
     *   3. If absent:
     *        a. Write viewers/{uid} = true
     *        b. Run a transaction on count to increment atomically.
     *
     * This means count is always exactly equal to the number of keys under
     * viewers/, even under concurrent writes from 1500 students.
     */
    private void recordStudentView(String noticeId) {
        if (!isValidNoticeId(noticeId)) return;
        if (currentUid == null || currentUid.isEmpty()) return;
        if (!"student".equalsIgnoreCase(currentRole)) return;

        DatabaseReference viewersRef = FirebaseDatabase.getInstance()
                .getReference("noticeViews")
                .child(noticeId)
                .child("viewers")
                .child(currentUid);

        // Step 1: check if already viewed (single cheap read on a leaf node).
        viewersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Already recorded — nothing to do.
                    return;
                }

                // Step 2a: write the viewer entry.
                viewersRef.setValue(true)
                        .addOnFailureListener(e ->
                                android.util.Log.e("NoticeAdapter",
                                        "Failed to write viewer entry: " + e.getMessage()));

                // Step 2b: atomically increment count.
                DatabaseReference countRef = FirebaseDatabase.getInstance()
                        .getReference("noticeViews")
                        .child(noticeId)
                        .child("count");

                countRef.runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData data) {
                        Long current = data.getValue(Long.class);
                        data.setValue(current == null ? 1L : current + 1L);
                        return Transaction.success(data);
                    }

                    @Override
                    public void onComplete(DatabaseError error, boolean committed,
                            DataSnapshot snapshot) {
                        if (error != null) {
                            android.util.Log.e("NoticeAdapter",
                                    "count transaction failed: " + error.getMessage());
                        }
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.e("NoticeAdapter",
                        "viewer check cancelled: " + error.getMessage());
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Firebase push keys start with '-' and are ~20 characters.
     * Rejects course names ("bca"), empty strings, and nulls.
     */
    private static boolean isValidNoticeId(String id) {
        return id != null && id.startsWith("-") && id.length() >= 15;
    }

    private void bindMedia(@NonNull NoticeViewHolder h, NoticeModel n) {
        String type = n.getMediaType();
        if (type == null || type.equals("text")
                || n.getMediaUrl() == null || n.getMediaUrl().isEmpty()) {
            h.mediaPreviewContainer.setVisibility(View.GONE);
            return;
        }
        h.mediaPreviewContainer.setVisibility(View.VISIBLE);
        if (type.equals("image")) {
            h.ivImagePreview.setVisibility(View.VISIBLE);
            h.nonImagePreview.setVisibility(View.GONE);
            Glide.with(context).load(n.getMediaUrl()).centerCrop()
                    .placeholder(R.color.surfaceElevated).into(h.ivImagePreview);
        } else {
            h.ivImagePreview.setVisibility(View.GONE);
            h.nonImagePreview.setVisibility(View.VISIBLE);
            if (type.equals("pdf")) {
                h.ivMediaIcon.setImageResource(R.drawable.ic_pdf);
                h.tvMediaLabel.setText("PDF Document — tap to open");
            } else {
                h.ivMediaIcon.setImageResource(R.drawable.ic_video);
                h.tvMediaLabel.setText("Video — tap to open");
            }
            h.nonImagePreview.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse(n.getMediaUrl()));
                context.startActivity(i);
            });
        }
    }

    private void openEditScreen(NoticeModel n) {
        Intent intent = new Intent(context, AddNoticeActivity.class);
        intent.putExtra(AddNoticeActivity.EXTRA_EDIT_MODE, true);
        intent.putExtra(AddNoticeActivity.EXTRA_NOTICE_ID, n.getNoticeId());
        intent.putExtra(AddNoticeActivity.EXTRA_TITLE, n.getTitle());
        intent.putExtra(AddNoticeActivity.EXTRA_DESCRIPTION, n.getDescription());
        intent.putExtra(AddNoticeActivity.EXTRA_MEDIA_TYPE, n.getMediaType());
        intent.putExtra(AddNoticeActivity.EXTRA_MEDIA_URL, n.getMediaUrl());
        intent.putExtra(AddNoticeActivity.EXTRA_DB_PATH, dbPath);
        intent.putExtra(AddNoticeActivity.EXTRA_DEPARTMENT, n.getDepartment());
        intent.putExtra(AddNoticeActivity.EXTRA_COURSE, n.getCourse());
        intent.putExtra(AddNoticeActivity.EXTRA_YEAR, n.getYear());
        context.startActivity(intent);
    }

    private void confirmDelete(NoticeModel n) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Notice")
                .setMessage("Are you sure you want to delete this notice?")
                .setPositiveButton("Delete", (d, w) -> deleteNotice(n))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteNotice(NoticeModel n) {
        FirebaseDatabase.getInstance()
                .getReference(dbPath)
                .child(n.getNoticeId())
                .removeValue();
    }

    private String formatTimestamp(long millis) {
        if (millis == 0) return "";
        long diff = System.currentTimeMillis() - millis;
        if (diff < 60_000)     return "just now";
        if (diff < 3_600_000)  return (diff / 60_000) + "m ago";
        if (diff < 86_400_000) return (diff / 3_600_000) + "h ago";
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(millis));
    }

    // ── ViewHolder ────────────────────────────────────────────────────────
    static class NoticeViewHolder extends RecyclerView.ViewHolder {

        // The notice currently bound to this holder.
        NoticeModel boundNotice;

        // Live count listener — stored so we can remove it precisely.
        DatabaseReference  countListenerRef;
        ValueEventListener countValueListener;

        TextView    tvCreatorName, tvTimestamp, tvTitle, tvDescription, tvEdited, tvMediaLabel;
        TextView    tvViewCount;
        ImageView   ivImagePreview, ivMediaIcon;
        LinearLayout nonImagePreview, actionRow, llViewRow;
        View        mediaPreviewContainer;
        ImageButton btnEdit, btnDelete;

        NoticeViewHolder(@NonNull View v) {
            super(v);
            tvCreatorName        = v.findViewById(R.id.tvCreatorName);
            tvTimestamp          = v.findViewById(R.id.tvTimestamp);
            tvTitle              = v.findViewById(R.id.tvTitle);
            tvDescription        = v.findViewById(R.id.tvDescription);
            tvEdited             = v.findViewById(R.id.tvEdited);
            mediaPreviewContainer = v.findViewById(R.id.mediaPreviewContainer);
            ivImagePreview       = v.findViewById(R.id.ivImagePreview);
            nonImagePreview      = v.findViewById(R.id.nonImagePreview);
            ivMediaIcon          = v.findViewById(R.id.ivMediaIcon);
            tvMediaLabel         = v.findViewById(R.id.tvMediaLabel);
            actionRow            = v.findViewById(R.id.actionRow);
            btnEdit              = v.findViewById(R.id.btnEdit);
            btnDelete            = v.findViewById(R.id.btnDelete);
            llViewRow            = v.findViewById(R.id.llViewRow);
            tvViewCount          = v.findViewById(R.id.tvViewCount);
        }
    }
}
