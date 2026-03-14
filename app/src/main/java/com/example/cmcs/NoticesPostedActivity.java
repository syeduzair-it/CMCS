package com.example.cmcs;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cmcs.models.NoticeModel;
import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NoticesPostedActivity — Teacher only.
 *
 * Aggregates all notices created by the current teacher across: •
 * notices/college • notices/training • notices/class/<department> (all courses
 * & years)
 *
 * Each notice is associated with the Firebase path it lives at so that edit and
 * delete work correctly regardless of notice category.
 *
 * Students should never reach this screen; a role-guard is included as a safety
 * net.
 */
public class NoticesPostedActivity extends AppCompatActivity {

    // Three parallel fan-out reads: college, training, class
    private static final int TOTAL_READS = 3;

    private ProgressBar loading;
    private TextView tvEmpty;
    private RecyclerView rv;

    private String currentUid;

    /**
     * Accumulates (notice, dbPath) pairs from all three parallel reads.
     */
    private final List<NoticeEntry> accumulated = new ArrayList<>();

    /**
     * Counts how many of the three reads have finished.
     */
    private final AtomicInteger completedReads = new AtomicInteger(0);

    // ── Lifecycle ────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notices_posted);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        loading = findViewById(R.id.np_loading);
        tvEmpty = findViewById(R.id.np_empty);
        rv = findViewById(R.id.np_rv);
        rv.setLayoutManager(new LinearLayoutManager(this));

        MaterialToolbar toolbar = findViewById(R.id.np_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        currentUid = user.getUid();

        loadDepartmentThenFetch();
    }

    // ── Step 1: resolve teacher's department ─────────────────────────────
    private void loadDepartmentThenFetch() {
        FirebaseDatabase.getInstance()
                .getReference("users").child(currentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String role = snapshot.child("role").getValue(String.class);
                        if (!"teacher".equals(role)) {
                            loading.setVisibility(View.GONE);
                            tvEmpty.setVisibility(View.VISIBLE);
                            return;
                        }
                        String dept = snapshot.child("department").getValue(String.class);
                        if (dept == null) {
                            dept = "";
                        }
                        fetchAllNotices(dept);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        loading.setVisibility(View.GONE);
                        Toast.makeText(NoticesPostedActivity.this,
                                "Failed to load profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ── Step 2: fire three parallel reads ────────────────────────────────
    private void fetchAllNotices(String dept) {
        fetchFlat("notices/college");
        fetchFlat("notices/training");
        fetchClassNotices(dept);
    }

    /**
     * Reads all children of a flat path (college or training). Adds matching
     * entries with that path as their dbPath.
     */
    private void fetchFlat(String path) {
        FirebaseDatabase.getInstance().getReference(path)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            NoticeModel n = child.getValue(NoticeModel.class);
                            if (n == null) {
                                continue;
                            }
                            n.setNoticeId(child.getKey());
                            if (currentUid.equals(n.getCreatedByUid())) {
                                synchronized (accumulated) {
                                    accumulated.add(new NoticeEntry(n, path));
                                }
                            }
                        }
                        onReadComplete();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        onReadComplete();
                    }
                });
    }

    /**
     * Traverses notices/class/<dept>/<course>/<year> and adds entries whose
     * dbPath is the leaf-level path (notices/class/<dept>/<course>/<year>).
     */
    private void fetchClassNotices(String dept) {
        String sanitizedDept = sanitize(dept);
        FirebaseDatabase.getInstance()
                .getReference("notices/class").child(sanitizedDept)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot deptSnap) {
                        for (DataSnapshot courseSnap : deptSnap.getChildren()) {
                            String course = courseSnap.getKey();
                            if (course == null) {
                                continue;
                            }

                            for (DataSnapshot yearSnap : courseSnap.getChildren()) {
                                String year = yearSnap.getKey();
                                if (year == null) {
                                    continue;
                                }

                                // This is the exact path where delete/edit must operate
                                String leafPath = "notices/class/"
                                        + sanitizedDept + "/" + course + "/" + year;

                                for (DataSnapshot noticeSnap : yearSnap.getChildren()) {
                                    NoticeModel n = noticeSnap.getValue(NoticeModel.class);
                                    if (n == null) {
                                        continue;
                                    }
                                    n.setNoticeId(noticeSnap.getKey());

                                    // Back-fill fields for older notices that may lack them
                                    if (n.getDepartment() == null) {
                                        n.setDepartment(dept);
                                    }
                                    if (n.getCourse() == null) {
                                        n.setCourse(unsanitize(course));
                                    }
                                    if (n.getYear() == null) {
                                        n.setYear(unsanitize(year));
                                    }

                                    if (currentUid.equals(n.getCreatedByUid())) {
                                        synchronized (accumulated) {
                                            accumulated.add(new NoticeEntry(n, leafPath));
                                        }
                                    }
                                }
                            }
                        }
                        onReadComplete();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        onReadComplete();
                    }
                });
    }

    // ── Step 3: render once all three reads finish ────────────────────────
    private void onReadComplete() {
        if (completedReads.incrementAndGet() < TOTAL_READS) {
            return;
        }
        runOnUiThread(this::renderResults);
    }

    private void renderResults() {
        loading.setVisibility(View.GONE);

        if (accumulated.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
            return;
        }

        // Sort newest first
        Collections.sort(accumulated,
                (a, b) -> Long.compare(b.notice.getTimestamp(), a.notice.getTimestamp()));

        tvEmpty.setVisibility(View.GONE);
        rv.setVisibility(View.VISIBLE);
        rv.setAdapter(new PostedAdapter(this, accumulated, currentUid));
    }

    // ── Sanitize helpers (mirrors NotesClassActivity / ClassNoticeFragment) ─
    static String sanitize(String s) {
        if (s == null) {
            return "_";
        }
        return s.trim().replace(" ", "_").replace(".", "_")
                .replace("#", "_").replace("$", "_")
                .replace("[", "_").replace("]", "_");
    }

    static String unsanitize(String s) {
        return s == null ? "" : s.replace("_", " ");
    }

    // ── Model: notice + its Firebase leaf path ────────────────────────────
    static class NoticeEntry {

        final NoticeModel notice;
        final String dbPath;   // e.g. "notices/college" or "notices/class/CS/BCA/1"

        NoticeEntry(NoticeModel notice, String dbPath) {
            this.notice = notice;
            this.dbPath = dbPath;
        }
    }

    // ── Self-contained RecyclerView adapter ──────────────────────────────
    /**
     * Standalone adapter for the Notices Posted list.
     *
     * We do NOT extend NoticeAdapter because that class holds a single fixed
     * dbPath for the whole list. Here every notice can live at a different
     * path, so each item carries its own dbPath in NoticeEntry.
     */
    static class PostedAdapter extends RecyclerView.Adapter<PostedAdapter.VH> {

        private final Context ctx;
        private final List<NoticeEntry> entries;
        private final String currentUid;

        PostedAdapter(Context ctx, List<NoticeEntry> entries, String uid) {
            this.ctx = ctx;
            this.entries = entries;
            this.currentUid = uid;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx)
                    .inflate(R.layout.item_notice, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            NoticeEntry entry = entries.get(position);
            NoticeModel notice = entry.notice;

            h.tvCreatorName.setText(notice.getCreatedByName() != null
                    ? notice.getCreatedByName() : "");
            h.tvTitle.setText(notice.getTitle());
            h.tvDescription.setText(notice.getDescription());
            h.tvEdited.setVisibility(notice.isEdited() ? View.VISIBLE : View.GONE);
            h.tvTimestamp.setText(formatTimestamp(notice.getTimestamp()));

            bindMedia(h, notice);

            // Edit/Delete always visible — this screen is teacher-only
            h.actionRow.setVisibility(View.VISIBLE);
            h.btnEdit.setOnClickListener(v -> openEditScreen(notice, entry.dbPath));
            h.btnDelete.setOnClickListener(v -> confirmDelete(position, notice, entry.dbPath));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        // ── Media binding ─────────────────────────────────────────────────
        private void bindMedia(VH h, NoticeModel n) {
            String type = n.getMediaType();
            if (type == null || type.equals("text")
                    || n.getMediaUrl() == null || n.getMediaUrl().isEmpty()) {
                h.mediaPreviewContainer.setVisibility(View.GONE);
                return;
            }
            h.mediaPreviewContainer.setVisibility(View.VISIBLE);

            if ("image".equals(type)) {
                h.ivImagePreview.setVisibility(View.VISIBLE);
                h.nonImagePreview.setVisibility(View.GONE);
                Glide.with(ctx).load(n.getMediaUrl())
                        .centerCrop()
                        .placeholder(R.color.surfaceElevated)
                        .into(h.ivImagePreview);
            } else {
                h.ivImagePreview.setVisibility(View.GONE);
                h.nonImagePreview.setVisibility(View.VISIBLE);
                if ("pdf".equals(type)) {
                    h.ivMediaIcon.setImageResource(R.drawable.ic_pdf);
                    h.tvMediaLabel.setText("PDF Document — tap to open");
                } else {
                    h.ivMediaIcon.setImageResource(R.drawable.ic_video);
                    h.tvMediaLabel.setText("Video — tap to open");
                }
                h.nonImagePreview.setOnClickListener(v -> {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse(n.getMediaUrl()));
                    ctx.startActivity(i);
                });
            }
        }

        // ── Edit ──────────────────────────────────────────────────────────
        private void openEditScreen(NoticeModel n, String dbPath) {
            Intent intent = new Intent(ctx, AddNoticeActivity.class);
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
            ctx.startActivity(intent);
        }

        // ── Delete ────────────────────────────────────────────────────────
        private void confirmDelete(int pos, NoticeModel n, String dbPath) {
            new AlertDialog.Builder(ctx)
                    .setTitle("Delete Notice")
                    .setMessage("Are you sure you want to delete this notice?")
                    .setPositiveButton("Delete", (d, w) -> deleteNotice(pos, n, dbPath))
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void deleteNotice(int pos, NoticeModel n, String dbPath) {
            // Remove DB node
            FirebaseDatabase.getInstance()
                    .getReference(dbPath)
                    .child(n.getNoticeId())
                    .removeValue()
                    .addOnSuccessListener(unused -> {
                        entries.remove(pos);
                        notifyItemRemoved(pos);
                        notifyItemRangeChanged(pos, entries.size());
                    });

            // Note: Cloudinary assets cannot be deleted from the client without
            // an API secret. DB record is removed above; Cloudinary URL is orphaned.
        }

        // ── Timestamp formatter ───────────────────────────────────────────
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

        // ── ViewHolder ────────────────────────────────────────────────────
        static class VH extends RecyclerView.ViewHolder {

            TextView tvCreatorName, tvTimestamp, tvTitle, tvDescription, tvEdited, tvMediaLabel;
            ImageView ivImagePreview, ivMediaIcon;
            LinearLayout nonImagePreview, actionRow;
            View mediaPreviewContainer;
            ImageButton btnEdit, btnDelete;

            VH(@NonNull View v) {
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
            }
        }
    }
}
