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
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoticeAdapter extends RecyclerView.Adapter<NoticeAdapter.NoticeViewHolder> {

    private final Context context;
    private final List<NoticeModel> notices;
    private final String currentUid;
    private final String currentRole;
    /**
     * Firebase DB path where these notices live, e.g. "notices/college"
     */
    private final String dbPath;

    public NoticeAdapter(Context context, List<NoticeModel> notices,
            String currentUid, String currentRole, String dbPath) {
        this.context = context;
        this.notices = notices;
        this.currentUid = currentUid;
        this.currentRole = currentRole;
        this.dbPath = dbPath;
    }

    @NonNull
    @Override
    public NoticeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_notice, parent, false);
        return new NoticeViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull NoticeViewHolder h, int position) {
        NoticeModel n = notices.get(position);

        h.tvCreatorName.setText(n.getCreatedByName() != null ? n.getCreatedByName() : "");
        h.tvTitle.setText(n.getTitle());
        h.tvDescription.setText(n.getDescription());
        h.tvEdited.setVisibility(n.isEdited() ? View.VISIBLE : View.GONE);

        // Timestamp
        h.tvTimestamp.setText(formatTimestamp(n.getTimestamp()));

        // Media preview
        bindMedia(h, n);

        // Edit / Delete — visible only to the creator who is a teacher
        boolean isOwner = "teacher".equals(currentRole)
                && currentUid != null
                && currentUid.equals(n.getCreatedByUid());
        h.actionRow.setVisibility(isOwner ? View.VISIBLE : View.GONE);

        if (isOwner) {
            h.btnEdit.setOnClickListener(v -> openEditScreen(n));
            h.btnDelete.setOnClickListener(v -> confirmDelete(n));
        }

        // ── View tracking ──────────────────────────────────────────────────
        if ("teacher".equalsIgnoreCase(currentRole)) {
            // Teachers see the live view count (single read, refreshed on each bind)
            h.llViewRow.setVisibility(View.VISIBLE);
            loadViewCount(h, n.getNoticeId());

            // Tap to open viewer list
            h.llViewRow.setOnClickListener(v -> {
                Intent intent = new Intent(context, NoticeViewersActivity.class);
                intent.putExtra(NoticeViewersActivity.EXTRA_NOTICE_ID, n.getNoticeId());
                intent.putExtra(NoticeViewersActivity.EXTRA_NOTICE_TITLE,
                        n.getTitle() != null ? n.getTitle() : "Notice");
                context.startActivity(intent);
            });

        } else {
            // Students: hide the view count row entirely
            h.llViewRow.setVisibility(View.GONE);
            h.llViewRow.setOnClickListener(null);

            // Record view on card tap (once per student — Firebase ignores duplicate writes
            // because the student UID key already exists with the same value `true`)
            h.itemView.setOnClickListener(v -> recordStudentView(n.getNoticeId()));
        }
    }

    @Override
    public int getItemCount() {
        return notices.size();
    }

    // ── View tracking helpers ─────────────────────────────────────────────
    /**
     * Single read of noticeViews/{noticeId} → count children → update chip.
     * Using addListenerForSingleValueEvent avoids keeping persistent listeners
     * for every visible notice card (important for ~1500 active users).
     */
    private void loadViewCount(@NonNull NoticeViewHolder h, String noticeId) {
        if (noticeId == null || noticeId.isEmpty()) {
            return;
        }

        FirebaseDatabase.getInstance()
                .getReference("noticeViews")
                .child(noticeId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        long count = snapshot.getChildrenCount();
                        h.tvViewCount.setText("\uD83D\uDC41 " + count + (count == 1 ? " view" : " views"));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Keep showing 0; no crash
                    }
                });
    }

    /**
     * Record that the current student has viewed this notice. Using the student
     * UID as the key guarantees no duplicates — a second setValue(true) on an
     * existing key is a no-op in RTDB. Teacher views are never recorded.
     */
    private void recordStudentView(String noticeId) {
        if (noticeId == null || noticeId.isEmpty()) {
            return;
        }
        if (currentUid == null || currentUid.isEmpty()) {
            return;
        }
        if (!"student".equalsIgnoreCase(currentRole)) {
            return;
        }

        FirebaseDatabase.getInstance()
                .getReference("noticeViews")
                .child(noticeId)
                .child(currentUid)
                .setValue(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private void bindMedia(@NonNull NoticeViewHolder h, NoticeModel n) {
        String type = n.getMediaType();
        if (type == null || type.equals("text") || n.getMediaUrl() == null || n.getMediaUrl().isEmpty()) {
            h.mediaPreviewContainer.setVisibility(View.GONE);
            return;
        }

        h.mediaPreviewContainer.setVisibility(View.VISIBLE);

        if (type.equals("image")) {
            h.ivImagePreview.setVisibility(View.VISIBLE);
            h.nonImagePreview.setVisibility(View.GONE);
            Glide.with(context)
                    .load(n.getMediaUrl())
                    .centerCrop()
                    .placeholder(R.color.surfaceElevated)
                    .into(h.ivImagePreview);
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
            // Tap to open URL
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
        // 1. Remove DB entry
        FirebaseDatabase.getInstance()
                .getReference(dbPath)
                .child(n.getNoticeId())
                .removeValue();

        // Note: Cloudinary assets cannot be deleted from the client without
        // an API secret. DB record is removed above; Cloudinary URL is orphaned.
    }

    private String formatTimestamp(long millis) {
        if (millis == 0) {
            return "";
        }
        long diff = System.currentTimeMillis() - millis;
        if (diff < 60_000) {
            return "just now";
        }
        if (diff < 3_600_000) {
            return (diff / 60_000) + "m ago";
        }
        if (diff < 86_400_000) {
            return (diff / 3_600_000) + "h ago";
        }
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(millis));
    }

    // ── ViewHolder ────────────────────────────────────────────────────────
    static class NoticeViewHolder extends RecyclerView.ViewHolder {

        TextView tvCreatorName, tvTimestamp, tvTitle, tvDescription, tvEdited, tvMediaLabel;
        TextView tvViewCount;
        ImageView ivImagePreview, ivMediaIcon;
        LinearLayout nonImagePreview, actionRow, llViewRow;
        View mediaPreviewContainer;
        ImageButton btnEdit, btnDelete;

        NoticeViewHolder(@NonNull View v) {
            super(v);
            tvCreatorName = v.findViewById(R.id.tvCreatorName);
            tvTimestamp = v.findViewById(R.id.tvTimestamp);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvDescription = v.findViewById(R.id.tvDescription);
            tvEdited = v.findViewById(R.id.tvEdited);
            mediaPreviewContainer = v.findViewById(R.id.mediaPreviewContainer);
            ivImagePreview = v.findViewById(R.id.ivImagePreview);
            nonImagePreview = v.findViewById(R.id.nonImagePreview);
            ivMediaIcon = v.findViewById(R.id.ivMediaIcon);
            tvMediaLabel = v.findViewById(R.id.tvMediaLabel);
            actionRow = v.findViewById(R.id.actionRow);
            btnEdit = v.findViewById(R.id.btnEdit);
            btnDelete = v.findViewById(R.id.btnDelete);
            llViewRow = v.findViewById(R.id.llViewRow);
            tvViewCount = v.findViewById(R.id.tvViewCount);
        }
    }
}
