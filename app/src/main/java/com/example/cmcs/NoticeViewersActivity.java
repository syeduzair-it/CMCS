package com.example.cmcs;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.adapters.StoryViewersAdapter;
import com.example.cmcs.models.ViewerModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the list of students who viewed a particular notice.
 *
 * Flow: 1. Read noticeViews/{noticeId} — keys are student authUids, values are
 * `true`. 2. For each authUid, query students/
 * orderByChild("authUid").equalTo(uid) to retrieve name + profileImage from the
 * push-key node. 3. Display results in a RecyclerView using StoryViewersAdapter
 * + item_viewer.xml.
 *
 * Only accessible to teachers (callers must enforce this).
 */
public class NoticeViewersActivity extends AppCompatActivity {

    public static final String EXTRA_NOTICE_ID = "notice_id";
    public static final String EXTRA_NOTICE_TITLE = "notice_title";

    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView tvNoViewers;

    private final List<ViewerModel> viewerList = new ArrayList<>();
    private StoryViewersAdapter adapter;

    // Track async completion: we fire N student lookups in parallel; when all are
    // done we reveal the list (or the empty state).
    private int pendingLookups = 0;
    private boolean lookupsDone = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice_viewers);

        String noticeId = getIntent().getStringExtra(EXTRA_NOTICE_ID);
        String noticeTitle = getIntent().getStringExtra(EXTRA_NOTICE_TITLE);

        MaterialToolbar toolbar = findViewById(R.id.toolbarNoticeViewers);
        toolbar.setTitle(noticeTitle != null ? noticeTitle : "Viewers");
        toolbar.setNavigationOnClickListener(v -> finish());
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        progressBar = findViewById(R.id.progressBarViewers);
        recyclerView = findViewById(R.id.rvNoticeViewers);
        tvNoViewers = findViewById(R.id.tvNoViewers);

        adapter = new StoryViewersAdapter(this, viewerList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        if (noticeId == null || noticeId.isEmpty()) {
            showEmptyState();
            return;
        }

        loadViewers(noticeId);
    }

    // ── Data loading ──────────────────────────────────────────────────────
    /**
     * Step 1 — Read noticeViews/{noticeId} to get all viewer UIDs.
     */
    private void loadViewers(String noticeId) {
        FirebaseDatabase.getInstance()
                .getReference("noticeViews")
                .child(noticeId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
                            showEmptyState();
                            return;
                        }

                        // Count how many parallel student lookups we need
                        pendingLookups = (int) snapshot.getChildrenCount();

                        for (DataSnapshot child : snapshot.getChildren()) {
                            String authUid = child.getKey();   // key = student authUid
                            fetchStudentByAuthUid(authUid);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showEmptyState();
                    }
                });
    }

    /**
     * Step 2 — For each authUid from noticeViews, query the students node using
     * orderByChild("authUid") because students/ uses Firebase push-keys, NOT
     * authUids, as the node key.
     */
    private void fetchStudentByAuthUid(String authUid) {
        if (authUid == null || authUid.isEmpty()) {
            onLookupComplete(null);
            return;
        }

        FirebaseDatabase.getInstance()
                .getReference("students")
                .orderByChild("authUid")
                .equalTo(authUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        ViewerModel viewer = null;
                        if (snapshot.exists()) {
                            // orderByChild returns matching children; take the first one
                            for (DataSnapshot child : snapshot.getChildren()) {
                                String name = child.child("name").getValue(String.class);
                                String profileImage = child.child("profileImage").getValue(String.class);

                                viewer = new ViewerModel(
                                        authUid,
                                        name != null ? name : "Unknown Student",
                                        profileImage,
                                        0L // timestamp not stored in noticeViews
                                );
                                break;
                            }
                        }
                        onLookupComplete(viewer);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        onLookupComplete(null);
                    }
                });
    }

    /**
     * Called after each student lookup finishes. When all lookups are done,
     * updates the UI.
     */
    private void onLookupComplete(ViewerModel viewer) {
        if (viewer != null) {
            viewerList.add(viewer);
        }
        pendingLookups--;

        if (pendingLookups <= 0 && !lookupsDone) {
            lookupsDone = true;
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (viewerList.isEmpty()) {
                    showEmptyState();
                } else {
                    adapter.notifyDataSetChanged();
                    recyclerView.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────
    private void showEmptyState() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
            tvNoViewers.setVisibility(View.VISIBLE);
        });
    }
}
