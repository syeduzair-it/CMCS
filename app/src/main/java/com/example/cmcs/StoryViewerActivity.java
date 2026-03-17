package com.example.cmcs;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.cmcs.models.StoryModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import android.util.Log;
import android.widget.Toast;
import android.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.cmcs.models.ViewerModel;
import com.example.cmcs.adapters.StoryViewersAdapter;

/**
 * StoryViewerActivity — Full-screen story viewer.
 *
 * • Receives a list of StoryModel (one teacher's group, or a single college
 * story). • Sorts them ASC by timestamp on arrival so playback is always oldest
 * → newest. • Supports an optional EXTRA_START_INDEX to begin at a specific
 * position. • Image stories: displayed for 5 s with animated progress bar. •
 * Video stories: played until completion; progress bar advances via polling. •
 * Instagram-style segmented progress bar strip at the top. • Left/right tap
 * zones to jump previous/next. • Teacher name + relative upload time shown for
 * every story.
 */
public class StoryViewerActivity extends AppCompatActivity {

    public static final String EXTRA_STORIES = "stories";
    public static final String EXTRA_START_INDEX = "start_index";

    // Image display duration in ms
    private static final long IMAGE_DURATION_MS = 5_000L;
    // Poll interval for video progress (ms)
    private static final long VIDEO_POLL_MS = 100L;

    private ImageView ivImage;
    private VideoView vvVideo;
    private LinearLayout llProgressBars;
    private TextView tvUploaderName;
    private TextView tvTimestamp;

    private ArrayList<StoryModel> stories;
    private int currentIndex;

    // Progress bars — one per story
    private ProgressBar[] bars;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable advanceRunnable = null;
    private Runnable videoProgressRunnable = null;

    // ── Uploader Controls ──────────────────────────────────────────────────
    private LinearLayout llUploaderControls;
    private LinearLayout btnViewers;
    private TextView tvViewCount;
    private ImageView btnDeleteStory;
    private ValueEventListener viewersCountListener = null;
    private DatabaseReference currentStoryViewersRef = null;

    // ── Lifecycle ──────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Full-screen immersive
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_story_viewer);

        ivImage = findViewById(R.id.ivStoryImage);
        vvVideo = findViewById(R.id.vvStoryVideo);
        llProgressBars = findViewById(R.id.llProgressBars);
        tvUploaderName = findViewById(R.id.tvUploaderName);
        tvTimestamp = findViewById(R.id.tvTimestamp);

        stories = getIntent().getParcelableArrayListExtra(EXTRA_STORIES);
        int startIndex = getIntent().getIntExtra(EXTRA_START_INDEX, 0);

        if (stories == null || stories.isEmpty()) {
            finish();
            return;
        }

        // Always sort ASC (oldest → newest) regardless of what the caller sent
        Collections.sort(stories,
                (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        // Clamp start index to valid range
        currentIndex = Math.max(0, Math.min(startIndex, stories.size() - 1));

        // Close button
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        // Tap zones
        findViewById(R.id.tapLeft).setOnClickListener(v -> moveTo(currentIndex - 1));
        findViewById(R.id.tapRight).setOnClickListener(v -> moveTo(currentIndex + 1));

        // Uploader Controls
        llUploaderControls = findViewById(R.id.llUploaderControls);
        btnViewers = findViewById(R.id.btnViewers);
        tvViewCount = findViewById(R.id.tvViewCount);
        btnDeleteStory = findViewById(R.id.btnDeleteStory);

        btnDeleteStory.setOnClickListener(v -> confirmDeleteStory());
        btnViewers.setOnClickListener(v -> showViewersBottomSheet());

        buildProgressBars();
        showStory(currentIndex);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimers();
        if (vvVideo.isPlaying()) {
            vvVideo.stopPlayback();
        }
        if (currentStoryViewersRef != null && viewersCountListener != null) {
            currentStoryViewersRef.removeEventListener(viewersCountListener);
        }
    }

    // ── Progress bar strip ─────────────────────────────────────────────────
    private void buildProgressBars() {
        llProgressBars.removeAllViews();
        bars = new ProgressBar[stories.size()];

        for (int i = 0; i < stories.size(); i++) {
            ProgressBar pb = new ProgressBar(
                    this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(1000);
            pb.setProgress(0);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, 4, 1f);   // equal weight, 4 dp tall
            lp.setMarginStart(i == 0 ? 0 : 4);
            pb.setLayoutParams(lp);
            pb.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(0xFFFFFFFF));  // white
            pb.setProgressBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0x80FFFFFF));  // semi-white

            llProgressBars.addView(pb);
            bars[i] = pb;
        }
    }

    /**
     * Fills bars before currentIndex, resets current + future to 0.
     */
    private void syncProgressBars() {
        for (int i = 0; i < bars.length; i++) {
            bars[i].setProgress(i < currentIndex ? 1000 : 0);
        }
    }

    // ── Story display ──────────────────────────────────────────────────────
    private void showStory(int index) {
        if (index < 0 || index >= stories.size()) {
            finish();
            return;
        }
        currentIndex = index;
        cancelTimers();
        syncProgressBars();

        StoryModel story = stories.get(index);

        // Uploader name — show for all story types when available
        String name = story.getTeacherName();
        if (!TextUtils.isEmpty(name)) {
            tvUploaderName.setVisibility(View.VISIBLE);
            tvUploaderName.setText(name);
        } else {
            tvUploaderName.setVisibility(View.GONE);
        }

        // Relative timestamp
        String relTime = relativeTime(story.getTimestamp());
        if (relTime != null) {
            tvTimestamp.setVisibility(View.VISIBLE);
            tvTimestamp.setText("· " + relTime);
        } else {
            tvTimestamp.setVisibility(View.GONE);
        }

        // ── Viewer Tracking & Uploader UI ──
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();

            // Check if uploader
            boolean isUploader = false;
            if ("college".equals(story.getType())) {
                // For college, if teacher uploaded it this teacherUid matches
                isUploader = uid.equals(story.getTeacherUid());
            } else {
                isUploader = uid.equals(story.getTeacherUid());
            }

            if (isUploader) {
                // Show controls, do not record self as viewer
                llUploaderControls.setVisibility(View.VISIBLE);
                listenToViewCount(story);
            } else {
                // Normal viewer
                llUploaderControls.setVisibility(View.GONE);
                recordViewerIfNeeded(story, currentUser);
            }
        } else {
            llUploaderControls.setVisibility(View.GONE);
        }

        if ("video".equals(story.getMediaType())) {
            showVideo(story);
        } else {
            showImage(story);
        }
    }

    // ── Image ──────────────────────────────────────────────────────────────
    private void showImage(StoryModel story) {
        vvVideo.setVisibility(View.GONE);
        ivImage.setVisibility(View.VISIBLE);

        Glide.with(this)
                .load(story.getMediaUrl())
                .thumbnail(0.3f)
                .into(ivImage);

        final ProgressBar pb = bars[currentIndex];
        final long startMs = System.currentTimeMillis();
        final int capturedIdx = currentIndex;

        Runnable ticker = new Runnable() {
            @Override
            public void run() {
                if (currentIndex != capturedIdx) {
                    return;
                }
                long elapsed = System.currentTimeMillis() - startMs;
                pb.setProgress((int) Math.min(1000L * elapsed / IMAGE_DURATION_MS, 1000));
                if (elapsed < IMAGE_DURATION_MS) {
                    handler.postDelayed(this, 50);
                }
            }
        };
        handler.post(ticker);

        advanceRunnable = () -> moveTo(currentIndex + 1);
        handler.postDelayed(advanceRunnable, IMAGE_DURATION_MS);
    }

    // ── Video ──────────────────────────────────────────────────────────────
    private void showVideo(StoryModel story) {
        ivImage.setVisibility(View.GONE);
        vvVideo.setVisibility(View.VISIBLE);

        vvVideo.setVideoURI(Uri.parse(story.getMediaUrl()));
        vvVideo.requestFocus();
        vvVideo.start();

        vvVideo.setOnCompletionListener(mp -> moveTo(currentIndex + 1));

        final ProgressBar pb = bars[currentIndex];
        final int capturedIdx = currentIndex;

        videoProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentIndex != capturedIdx || !vvVideo.isPlaying()) {
                    return;
                }
                int duration = vvVideo.getDuration();
                if (duration > 0) {
                    pb.setProgress((int) (1000L * vvVideo.getCurrentPosition() / duration));
                }
                handler.postDelayed(this, VIDEO_POLL_MS);
            }
        };
        vvVideo.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            handler.post(videoProgressRunnable);
        });
    }

    // ── Navigation ─────────────────────────────────────────────────────────
    private void moveTo(int index) {
        if (index < 0) {
            // At first story — restart it
            showStory(currentIndex);
            return;
        }
        if (index >= stories.size()) {
            finish();
            return;
        }
        showStory(index);
    }

    // ── Story Deletion ─────────────────────────────────────────────────────
    private void confirmDeleteStory() {
        cancelTimers(); // Pause playback
        if (vvVideo.isPlaying()) {
            vvVideo.pause();
        }

        new AlertDialog.Builder(this)
                .setMessage("Delete this story?")
                .setPositiveButton("Delete", (dialog, which) -> deleteStory())
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Resume playback
                    showStory(currentIndex);
                })
                .setOnCancelListener(dialog -> showStory(currentIndex))
                .show();
    }

    private void deleteStory() {
        if (currentIndex < 0 || currentIndex >= stories.size()) {
            return;
        }
        StoryModel story = stories.get(currentIndex);

        DatabaseReference ref;
        if ("college".equals(story.getType())) {
            ref = FirebaseDatabase.getInstance().getReference("stories/college").child(story.getStoryId());
        } else {
            ref = FirebaseDatabase.getInstance().getReference("stories/teachers")
                    .child(story.getTeacherUid()).child(story.getStoryId());
        }

        ref.removeValue().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Story deleted", Toast.LENGTH_SHORT).show();
            stories.remove(currentIndex);

            if (stories.isEmpty()) {
                finish();
            } else {
                // We removed the current item, so the next item is now at currentIndex.
                // Rebuild progress bars and show.
                if (currentIndex >= stories.size()) {
                    currentIndex = stories.size() - 1; // Go to new last item
                }
                buildProgressBars();
                showStory(currentIndex);
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            showStory(currentIndex); // resume
        });
    }

    // ── Viewer Tracking & UI ───────────────────────────────────────────────
    private String getStoryViewersPath(StoryModel story) {
        if ("college".equals(story.getType())) {
            return "stories/college/" + story.getStoryId() + "/viewers";
        } else {
            return "stories/teachers/" + story.getTeacherUid() + "/" + story.getStoryId() + "/viewers";
        }
    }

    private void recordViewerIfNeeded(StoryModel story, FirebaseUser currentUser) {
        String path = getStoryViewersPath(story);
        DatabaseReference viewerRef = FirebaseDatabase.getInstance().getReference(path).child(currentUser.getUid());

        // Single read check
        viewerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Record viewer
                    Map<String, Object> data = new HashMap<>();
                    data.put("name", currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Unknown");
                    data.put("timestamp", ServerValue.TIMESTAMP);
                    viewerRef.setValue(data);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore
            }
        });
    }

    private void listenToViewCount(StoryModel story) {
        if (currentStoryViewersRef != null && viewersCountListener != null) {
            currentStoryViewersRef.removeEventListener(viewersCountListener);
        }

        String path = getStoryViewersPath(story);
        currentStoryViewersRef = FirebaseDatabase.getInstance().getReference(path);

        viewersCountListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                tvViewCount.setText(String.valueOf(count));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore
            }
        };
        currentStoryViewersRef.addValueEventListener(viewersCountListener);
    }

    private void showViewersBottomSheet() {
        cancelTimers();
        if (vvVideo.isPlaying()) {
            vvVideo.pause();
        }

        if (currentIndex < 0 || currentIndex >= stories.size()) {
            return;
        }
        StoryModel story = stories.get(currentIndex);

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_viewers, null);
        dialog.setContentView(view);

        RecyclerView rvViewers = view.findViewById(R.id.rvViewers);
        TextView tvNoViewers = view.findViewById(R.id.tvNoViewers);

        rvViewers.setLayoutManager(new LinearLayoutManager(this));

        ArrayList<ViewerModel> viewersList = new ArrayList<>();
        StoryViewersAdapter adapter = new StoryViewersAdapter(this, viewersList);
        rvViewers.setAdapter(adapter);

        // Load viewers
        String path = getStoryViewersPath(story);
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                viewersList.clear();

                long totalViewers = snapshot.getChildrenCount();
                if (totalViewers == 0) {
                    rvViewers.setVisibility(View.GONE);
                    tvNoViewers.setVisibility(View.VISIBLE);
                    return;
                }

                rvViewers.setVisibility(View.VISIBLE);
                tvNoViewers.setVisibility(View.GONE);

                java.util.concurrent.atomic.AtomicInteger loadedCount = new java.util.concurrent.atomic.AtomicInteger(0);

                for (DataSnapshot child : snapshot.getChildren()) {
                    String uid = child.getKey();
                    Long timestamp = child.child("timestamp").getValue(Long.class);
                    if (timestamp == null) {
                        timestamp = 0L;
                    }
                    long finalTimestamp = timestamp;

                    // Fetch user details
                    FirebaseDatabase.getInstance().getReference("users").child(uid)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot userSnap) {
                                    String name = userSnap.child("name").getValue(String.class);
                                    String profileImage = userSnap.child("profileImage").getValue(String.class);
                                    String role = userSnap.child("role").getValue(String.class);

                                    if (name == null || name.isEmpty()) {
                                        name = "Unknown";
                                    }

                                    ViewerModel v = new ViewerModel(uid, name, profileImage, finalTimestamp);
                                    v.setRole(role);

                                    synchronized (viewersList) {
                                        viewersList.add(v);
                                    }

                                    if (loadedCount.incrementAndGet() == totalViewers) {
                                        Collections.sort(viewersList, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                                        adapter.notifyDataSetChanged();
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    if (loadedCount.incrementAndGet() == totalViewers) {
                                        Collections.sort(viewersList, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                                        adapter.notifyDataSetChanged();
                                    }
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore
            }
        });

        dialog.setOnDismissListener(d -> showStory(currentIndex));
        dialog.show();
    }

    // ── Timer cleanup ──────────────────────────────────────────────────────
    private void cancelTimers() {
        if (advanceRunnable != null) {
            handler.removeCallbacks(advanceRunnable);
            advanceRunnable = null;
        }
        if (videoProgressRunnable != null) {
            handler.removeCallbacks(videoProgressRunnable);
            videoProgressRunnable = null;
        }
    }

    // ── Relative time helper ───────────────────────────────────────────────
    /**
     * Returns a human-friendly relative time string.
     *
     * @param timestamp Unix time in ms
     * @return "Just now" / "Xm ago" / "Xh ago", or null if timestamp is 0
     */
    private static String relativeTime(long timestamp) {
        if (timestamp <= 0) {
            return null;
        }
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / 60_000L;
        if (minutes < 1) {
            return "Just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        return hours + "h ago";
    }
}
