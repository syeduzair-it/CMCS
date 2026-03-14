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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shows the list of students who viewed a particular notice.
 *
 * Flow:
 *  1. Read noticeViews/{noticeId} — keys are student authUids, values are true.
 *  2. For each authUid, query students/ orderByChild("authUid").equalTo(uid)
 *     to retrieve name + profileImage from the push-key node.
 *  3. Display results in a RecyclerView using StoryViewersAdapter.
 *
 * Thread-safety:
 *  Firebase callbacks arrive on the main thread, but we use AtomicInteger for
 *  the pending-lookup counter so the decrement-and-check is race-free even if
 *  the threading model ever changes.  viewerList is only ever touched on the
 *  main thread (all callbacks are on main thread in RTDB SDK).
 *
 * Only accessible to teachers (callers must enforce this).
 */
public class NoticeViewersActivity extends AppCompatActivity {

    public static final String EXTRA_NOTICE_ID    = "notice_id";
    public static final String EXTRA_NOTICE_TITLE = "notice_title";

    private ProgressBar        progressBar;
    private RecyclerView       recyclerView;
    private TextView           tvNoViewers;

    private final List<ViewerModel> viewerList = new ArrayList<>();
    private StoryViewersAdapter     adapter;

    // AtomicInteger so the decrement-and-check in onLookupComplete is race-free.
    private final AtomicInteger pendingLookups = new AtomicInteger(0);
    // Guard against showEmptyState / notifyDataSetChanged being called twice.
    private volatile boolean    lookupsDone    = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice_viewers);

        String noticeId    = getIntent().getStringExtra(EXTRA_NOTICE_ID);
        String noticeTitle = getIntent().getStringExtra(EXTRA_NOTICE_TITLE);

        MaterialToolbar toolbar = findViewById(R.id.toolbarNoticeViewers);
        toolbar.setTitle(noticeTitle != null ? noticeTitle : "Viewers");
        toolbar.setNavigationOnClickListener(v -> finish());
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        progressBar  = findViewById(R.id.progressBarViewers);
        recyclerView = findViewById(R.id.rvNoticeViewers);
        tvNoViewers  = findViewById(R.id.tvNoViewers);

        adapter = new StoryViewersAdapter(this, viewerList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        if (noticeId == null || noticeId.isEmpty()) {
            showEmptyState();
            return;
        }

        loadViewers(noticeId);
    }

    // ── Step 1: read all viewer UIDs ──────────────────────────────────────
    private void loadViewers(String noticeId) {
        android.util.Log.d("NoticeViewers", "loadViewers: noticeId=" + noticeId);

        FirebaseDatabase.getInstance()
                .getReference("noticeViews")
                .child(noticeId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        long count = snapshot.getChildrenCount();
                        android.util.Log.d("NoticeViewers",
                                "noticeViews children count=" + count);

                        if (!snapshot.exists() || count == 0) {
                            showEmptyState();
                            return;
                        }

                        // Set the counter BEFORE firing any async lookups so
                        // onLookupComplete can never see 0 before all are fired.
                        pendingLookups.set((int) count);

                        for (DataSnapshot child : snapshot.getChildren()) {
                            String authUid = child.getKey(); // key = student authUid
                            android.util.Log.d("NoticeViewers",
                                    "Queuing lookup for authUid=" + authUid);
                            fetchStudentByAuthUid(authUid);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        android.util.Log.e("NoticeViewers",
                                "loadViewers cancelled: " + error.getMessage());
                        showEmptyState();
                    }
                });
    }

    // ── Step 2: resolve each authUid → student record ─────────────────────
    /**
     * Students are stored under push-keys, NOT under their authUid.
     * We must use orderByChild("authUid").equalTo(uid) to find the record.
     */
    private void fetchStudentByAuthUid(String authUid) {
        if (authUid == null || authUid.isEmpty()) {
            android.util.Log.w("NoticeViewers", "fetchStudentByAuthUid: empty uid, skipping");
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

                        if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                            // orderByChild returns a node whose children are the matches.
                            // Take the first (and normally only) match.
                            for (DataSnapshot child : snapshot.getChildren()) {
                                String name         = child.child("name").getValue(String.class);
                                String profileImage = child.child("profileImage").getValue(String.class);

                                viewer = new ViewerModel(
                                        authUid,
                                        name != null ? name : "Unknown Student",
                                        profileImage,
                                        0L
                                );
                                android.util.Log.d("NoticeViewers",
                                        "Resolved authUid=" + authUid + " → name=" + name);
                                break;
                            }
                        } else {
                            // UID exists in noticeViews but not in students/ —
                            // the account was deleted or the UID was written incorrectly.
                            android.util.Log.w("NoticeViewers",
                                    "No student record for authUid=" + authUid);
                        }

                        onLookupComplete(viewer);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        android.util.Log.e("NoticeViewers",
                                "fetchStudent cancelled for authUid=" + authUid
                                        + ": " + error.getMessage());
                        onLookupComplete(null);
                    }
                });
    }

    // ── Step 3: collect results and update UI when all lookups finish ──────
    /**
     * Called on the main thread after each student lookup (success or failure).
     *
     * We decrement the AtomicInteger and, when it reaches zero, publish the
     * final list.  Using getAndDecrement() makes the decrement-and-read atomic,
     * preventing the race where two callbacks both see pendingLookups == 1 and
     * both try to publish.
     */
    private void onLookupComplete(ViewerModel viewer) {
        // viewerList is only touched here, which runs on the main thread.
        if (viewer != null) {
            viewerList.add(viewer);
        }

        int remaining = pendingLookups.decrementAndGet();
        android.util.Log.d("NoticeViewers",
                "onLookupComplete: remaining=" + remaining
                        + " viewerList.size=" + viewerList.size());

        if (remaining == 0 && !lookupsDone) {
            lookupsDone = true;
            progressBar.setVisibility(View.GONE);

            if (viewerList.isEmpty()) {
                android.util.Log.w("NoticeViewers",
                        "All lookups done but viewerList is empty — "
                                + "UIDs in noticeViews may not match any student record.");
                showEmptyState();
            } else {
                adapter.notifyDataSetChanged();
                recyclerView.setVisibility(View.VISIBLE);
                tvNoViewers.setVisibility(View.GONE);
                android.util.Log.d("NoticeViewers",
                        "Displaying " + viewerList.size() + " viewers");
            }
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
