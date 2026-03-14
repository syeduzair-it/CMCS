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
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.cmcs.AddNoticeActivity;
import com.example.cmcs.NoticeMediaViewerActivity;
import com.example.cmcs.NoticeVideoViewerActivity;
import com.example.cmcs.NoticeViewersActivity;
import com.example.cmcs.R;
import com.example.cmcs.models.NoticeModel;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
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
 *     noticeViews/{noticeId}/viewers/{uid} = true
 *
 *   Student path:
 *     onViewAttachedToWindow fires when a card genuinely enters the screen.
 *     We check viewers/{uid} first; only if absent do we write the entry.
 *     One write per student per notice, ever.
 *
 *   Teacher path:
 *     A single-event read on noticeViews/{noticeId}/viewers derives the count
 *     via snapshot.getChildrenCount(). No persistent listener needed.
 */
public class NoticeAdapter extends RecyclerView.Adapter<NoticeAdapter.NoticeViewHolder> {

    private final Context           context;
    private final List<NoticeModel> notices;
    private final String            currentUid;
    private final String            currentRole;
    private final String            dbPath;

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
            h.tvViewCount.setText("\uD83D\uDC41 0 views");
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
            h.itemView.setOnClickListener(null);
        }
    }

    // ── Attach / Detach ───────────────────────────────────────────────────

    @Override
    public void onViewAttachedToWindow(@NonNull NoticeViewHolder h) {
        super.onViewAttachedToWindow(h);
        NoticeModel n = h.boundNotice;
        if (n == null || !isValidNoticeId(n.getNoticeId())) return;

        if ("teacher".equalsIgnoreCase(currentRole)) {
            loadViewCount(h, n.getNoticeId());
        } else if ("student".equalsIgnoreCase(currentRole)) {
            recordStudentView(n.getNoticeId());
        }
    }

    @Override
    public void onViewRecycled(@NonNull NoticeViewHolder h) {
        super.onViewRecycled(h);
        h.boundNotice = null;
    }

    @Override
    public int getItemCount() {
        return notices.size();
    }

    // ── View count (teacher) ──────────────────────────────────────────────

    /**
     * Reads noticeViews/{noticeId}/viewers once and derives the count from
     * snapshot.getChildrenCount(). No persistent listener — teachers get a
     * fresh count each time the card appears on screen.
     */
    private void loadViewCount(@NonNull NoticeViewHolder h, String noticeId) {
        FirebaseDatabase.getInstance()
                .getReference("noticeViews")
                .child(noticeId)
                .child("viewers")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        long count = snapshot.getChildrenCount();
                        h.tvViewCount.setText("\uD83D\uDC41 " + count
                                + (count == 1 ? " view" : " views"));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        h.tvViewCount.setText("\uD83D\uDC41 0 views");
                        android.util.Log.e("NoticeAdapter",
                                "loadViewCount cancelled for " + noticeId
                                        + ": " + error.getMessage());
                    }
                });
    }

    // ── Student view recording ─────────────────────────────────────────────

    /**
     * Writes noticeViews/{noticeId}/viewers/{uid} = true, but only if the
     * entry doesn't already exist. One cheap leaf-node read guards against
     * duplicate writes.
     */
    private void recordStudentView(String noticeId) {
        if (!isValidNoticeId(noticeId)) return;
        if (currentUid == null || currentUid.isEmpty()) return;
        if (!"student".equalsIgnoreCase(currentRole)) return;

        DatabaseReference viewerRef = FirebaseDatabase.getInstance()
                .getReference("noticeViews")
                .child(noticeId)
                .child("viewers")
                .child(currentUid);

        viewerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) return; // already recorded

                viewerRef.setValue(true)
                        .addOnFailureListener(e ->
                                android.util.Log.e("NoticeAdapter",
                                        "Failed to write viewer entry: " + e.getMessage()));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.e("NoticeAdapter",
                        "viewer check cancelled: " + error.getMessage());
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Firebase push keys start with '-' and are ~20 characters. */
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
            Glide.with(context).load(n.getMediaUrl())
                    .fitCenter()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.color.surfaceElevated)
                    .into(h.ivImagePreview);
            // Tap → full-screen viewer with download + share
            h.ivImagePreview.setOnClickListener(v -> {
                Intent i = new Intent(context, NoticeMediaViewerActivity.class);
                i.putExtra(NoticeMediaViewerActivity.EXTRA_MEDIA_URL, n.getMediaUrl());
                i.putExtra(NoticeMediaViewerActivity.EXTRA_MEDIA_TYPE, "image");
                context.startActivity(i);
            });
        } else {
            h.ivImagePreview.setVisibility(View.GONE);
            h.nonImagePreview.setVisibility(View.VISIBLE);
            if (type.equals("pdf")) {
                h.ivMediaIcon.setImageResource(R.drawable.ic_pdf);
                h.tvMediaLabel.setText("PDF Document — tap to open");
                // PDF → full-screen viewer with download + share
                h.nonImagePreview.setOnClickListener(v -> {
                    Intent i = new Intent(context, NoticeMediaViewerActivity.class);
                    i.putExtra(NoticeMediaViewerActivity.EXTRA_MEDIA_URL, n.getMediaUrl());
                    i.putExtra(NoticeMediaViewerActivity.EXTRA_MEDIA_TYPE, "pdf");
                    context.startActivity(i);
                });
            } else {
                // Video → in-app ExoPlayer
                h.ivMediaIcon.setImageResource(R.drawable.ic_video);
                h.tvMediaLabel.setText("Video — tap to play");
                h.nonImagePreview.setOnClickListener(v -> {
                    Intent i = new Intent(context, NoticeVideoViewerActivity.class);
                    i.putExtra(NoticeVideoViewerActivity.EXTRA_VIDEO_URL, n.getMediaUrl());
                    context.startActivity(i);
                });
            }
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

        NoticeModel boundNotice;

        TextView     tvCreatorName, tvTimestamp, tvTitle, tvDescription, tvEdited, tvMediaLabel;
        TextView     tvViewCount;
        ImageView    ivImagePreview, ivMediaIcon;
        LinearLayout nonImagePreview, actionRow, llViewRow;
        View         mediaPreviewContainer;
        ImageButton  btnEdit, btnDelete;

        NoticeViewHolder(@NonNull View v) {
            super(v);
            tvCreatorName         = v.findViewById(R.id.tvCreatorName);
            tvTimestamp           = v.findViewById(R.id.tvTimestamp);
            tvTitle               = v.findViewById(R.id.tvTitle);
            tvDescription         = v.findViewById(R.id.tvDescription);
            tvEdited              = v.findViewById(R.id.tvEdited);
            mediaPreviewContainer = v.findViewById(R.id.mediaPreviewContainer);
            ivImagePreview        = v.findViewById(R.id.ivImagePreview);
            nonImagePreview       = v.findViewById(R.id.nonImagePreview);
            ivMediaIcon           = v.findViewById(R.id.ivMediaIcon);
            tvMediaLabel          = v.findViewById(R.id.tvMediaLabel);
            actionRow             = v.findViewById(R.id.actionRow);
            btnEdit               = v.findViewById(R.id.btnEdit);
            btnDelete             = v.findViewById(R.id.btnDelete);
            llViewRow             = v.findViewById(R.id.llViewRow);
            tvViewCount           = v.findViewById(R.id.tvViewCount);
        }
    }
}
