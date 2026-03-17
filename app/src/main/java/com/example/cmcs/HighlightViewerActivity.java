package com.example.cmcs;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.cmcs.models.HighlightMediaModel;
import com.example.cmcs.utils.CloudinaryUploader;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HighlightViewerActivity — Full-screen Instagram-style highlight viewer.
 *
 * Features:
 *  - Segmented progress bars (3dp, white)
 *  - Left/right tap zones for navigation
 *  - Hold-to-pause (ACTION_DOWN pauses, ACTION_UP resumes)
 *  - Teacher: add media / delete current item
 *  - Top gradient overlay for readability
 */
public class HighlightViewerActivity extends AppCompatActivity {

    public static final String EXTRA_HIGHLIGHT_ID    = "highlight_id";
    public static final String EXTRA_HIGHLIGHT_TITLE = "highlight_title";
    public static final String EXTRA_IS_TEACHER      = "is_teacher";
    public static final String EXTRA_CREATED_BY_UID  = "created_by_uid";

    private static final long IMAGE_DURATION_MS = 5_000L;
    private static final long VIDEO_POLL_MS     = 100L;

    // ── Views ─────────────────────────────────────────────────────────────
    private ImageView     ivImage;
    private VideoView     vvVideo;
    private LinearLayout  llProgressBars;
    private TextView      tvTitle;
    private View          emptyState;
    private ImageButton   btnAddMedia, btnDeleteMedia;

    // ── State ─────────────────────────────────────────────────────────────
    private final List<HighlightMediaModel> mediaList = new ArrayList<>();
    private int     currentIndex;
    private String  highlightId;
    private boolean isTeacher;
    private boolean isPaused = false;
    private boolean isVideo  = false;

    // ── Timers ────────────────────────────────────────────────────────────
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable advanceRunnable;
    private Runnable videoProgressRunnable;
    private Runnable imageTickRunnable;
    private ProgressBar[] bars = new ProgressBar[0];

    // Snapshot of elapsed ms when paused (image timer only)
    private long pausedElapsedMs = 0;
    private long imageStartMs    = 0;

    private final ActivityResultLauncher<String> mediaPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> { if (uri != null) uploadAdditionalMedia(uri); });

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_highlight_viewer);

        ivImage        = findViewById(R.id.hv_ivImage);
        vvVideo        = findViewById(R.id.hv_vvVideo);
        llProgressBars = findViewById(R.id.hv_llProgressBars);
        tvTitle        = findViewById(R.id.hv_tvTitle);
        emptyState     = findViewById(R.id.hv_emptyState);
        btnAddMedia    = findViewById(R.id.hv_btnAddMedia);
        btnDeleteMedia = findViewById(R.id.hv_btnDeleteMedia);

        highlightId = getIntent().getStringExtra(EXTRA_HIGHLIGHT_ID);
        isTeacher   = getIntent().getBooleanExtra(EXTRA_IS_TEACHER, false);
        tvTitle.setText(getIntent().getStringExtra(EXTRA_HIGHLIGHT_TITLE));

        // Back
        findViewById(R.id.hv_btnBack).setOnClickListener(v -> finish());

        // Tap zones — navigation
        findViewById(R.id.hv_tapLeft).setOnClickListener(v -> moveTo(currentIndex - 1));
        findViewById(R.id.hv_tapRight).setOnClickListener(v -> moveTo(currentIndex + 1));

        // Hold-to-pause on root
        setupHoldToPause();

        // Teacher controls
        if (isTeacher) {
            btnAddMedia.setVisibility(View.VISIBLE);
            btnDeleteMedia.setVisibility(View.VISIBLE);
            btnAddMedia.setOnClickListener(v -> {
                android.util.Log.d("HIGHLIGHT", "Add clicked");
                mediaPicker.launch("image/* video/*");
            });
            btnDeleteMedia.setOnClickListener(v -> {
                android.util.Log.d("HIGHLIGHT", "Delete clicked");
                confirmDeleteMedia();
            });
        }

        loadMedia();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimers();
        if (vvVideo.isPlaying()) vvVideo.stopPlayback();
    }

    // ── Hold-to-pause ─────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private void setupHoldToPause() {
        View root = findViewById(R.id.hv_root);
        root.setOnTouchListener((v, event) -> {
            // Let tap zones handle their own clicks; only intercept hold on the root
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    pauseProgress();
                    return false; // allow child views to still receive the event
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    resumeProgress();
                    return false;
            }
            return false;
        });
    }

    private void pauseProgress() {
        if (isPaused) return;
        isPaused = true;
        // Stop all pending callbacks
        if (advanceRunnable != null)       handler.removeCallbacks(advanceRunnable);
        if (imageTickRunnable != null)     handler.removeCallbacks(imageTickRunnable);
        if (videoProgressRunnable != null) handler.removeCallbacks(videoProgressRunnable);
        // Snapshot elapsed time for image timer
        if (!isVideo) {
            pausedElapsedMs = System.currentTimeMillis() - imageStartMs;
        }
        // Pause video
        if (isVideo && vvVideo.isPlaying()) vvVideo.pause();
    }

    private void resumeProgress() {
        if (!isPaused) return;
        isPaused = false;
        if (isVideo) {
            if (!vvVideo.isPlaying()) vvVideo.start();
            scheduleVideoProgress(currentIndex);
        } else {
            // Resume image timer from where it was paused
            long remaining = IMAGE_DURATION_MS - pausedElapsedMs;
            imageStartMs = System.currentTimeMillis() - pausedElapsedMs;
            resumeImageTicker(currentIndex, remaining);
        }
    }

    // ── Load media ────────────────────────────────────────────────────────
    private void loadMedia() {
        FirebaseDatabase.getInstance()
                .getReference("highlights").child(highlightId).child("media")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        mediaList.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            HighlightMediaModel m = child.getValue(HighlightMediaModel.class);
                            if (m == null) continue;
                            m.setMediaId(child.getKey());
                            mediaList.add(m);
                        }
                        Collections.sort(mediaList,
                                (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
                        if (mediaList.isEmpty()) {
                            emptyState.setVisibility(View.VISIBLE);
                        } else {
                            emptyState.setVisibility(View.GONE);
                            buildProgressBars();
                            showMedia(0);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ── Progress bars ─────────────────────────────────────────────────────
    private void buildProgressBars() {
        llProgressBars.removeAllViews();
        bars = new ProgressBar[mediaList.size()];
        int dp3 = (int) (3 * getResources().getDisplayMetrics().density);
        int dp2 = (int) (2 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < mediaList.size(); i++) {
            ProgressBar pb = new ProgressBar(this, null,
                    android.R.attr.progressBarStyleHorizontal);
            pb.setMax(1000);
            pb.setProgress(0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp3, 1f);
            lp.setMarginStart(i == 0 ? 0 : dp2);
            pb.setLayoutParams(lp);
            pb.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            pb.setProgressBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0x55FFFFFF));
            llProgressBars.addView(pb);
            bars[i] = pb;
        }
    }

    private void syncProgressBars() {
        for (int i = 0; i < bars.length; i++) {
            bars[i].setProgress(i < currentIndex ? 1000 : 0);
        }
    }

    // ── Media display ─────────────────────────────────────────────────────
    private void showMedia(int index) {
        if (index < 0 || index >= mediaList.size()) { finish(); return; }
        currentIndex = index;
        isPaused = false;
        pausedElapsedMs = 0;
        cancelTimers();
        syncProgressBars();
        HighlightMediaModel m = mediaList.get(index);
        isVideo = "video".equals(m.getMediaType());
        if (isVideo) showVideo(m);
        else         showImage(m);
    }

    private void showImage(HighlightMediaModel m) {
        vvVideo.setVisibility(View.GONE);
        ivImage.setVisibility(View.VISIBLE);
        Glide.with(this).load(m.getMediaUrl()).thumbnail(0.3f).into(ivImage);
        imageStartMs = System.currentTimeMillis();
        resumeImageTicker(currentIndex, IMAGE_DURATION_MS);
    }

    private void resumeImageTicker(int capIdx, long remaining) {
        final ProgressBar pb = bars[capIdx];
        imageTickRunnable = new Runnable() {
            @Override public void run() {
                if (isPaused || currentIndex != capIdx) return;
                long elapsed = System.currentTimeMillis() - imageStartMs;
                pb.setProgress((int) Math.min(1000L * elapsed / IMAGE_DURATION_MS, 1000));
                if (elapsed < IMAGE_DURATION_MS) handler.postDelayed(this, 50);
            }
        };
        handler.post(imageTickRunnable);
        advanceRunnable = () -> moveTo(currentIndex + 1);
        handler.postDelayed(advanceRunnable, remaining);
    }

    private void showVideo(HighlightMediaModel m) {
        ivImage.setVisibility(View.GONE);
        vvVideo.setVisibility(View.VISIBLE);
        vvVideo.setVideoURI(Uri.parse(m.getMediaUrl()));
        vvVideo.requestFocus();
        vvVideo.start();
        vvVideo.setOnCompletionListener(mp -> moveTo(currentIndex + 1));
        vvVideo.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            scheduleVideoProgress(currentIndex);
        });
    }

    private void scheduleVideoProgress(int capIdx) {
        final ProgressBar pb = bars[capIdx];
        videoProgressRunnable = new Runnable() {
            @Override public void run() {
                if (isPaused || currentIndex != capIdx || !vvVideo.isPlaying()) return;
                int dur = vvVideo.getDuration();
                if (dur > 0) pb.setProgress((int) (1000L * vvVideo.getCurrentPosition() / dur));
                handler.postDelayed(this, VIDEO_POLL_MS);
            }
        };
        handler.post(videoProgressRunnable);
    }

    private void moveTo(int index) {
        if (index < 0)                  { showMedia(currentIndex); return; }
        if (index >= mediaList.size())  { finish(); return; }
        showMedia(index);
    }

    private void cancelTimers() {
        if (advanceRunnable != null)       handler.removeCallbacks(advanceRunnable);
        if (imageTickRunnable != null)     handler.removeCallbacks(imageTickRunnable);
        if (videoProgressRunnable != null) handler.removeCallbacks(videoProgressRunnable);
        advanceRunnable = imageTickRunnable = videoProgressRunnable = null;
    }

    // ── Teacher: add media ────────────────────────────────────────────────
    private void uploadAdditionalMedia(Uri uri) {
        String mediaId = FirebaseDatabase.getInstance().getReference().push().getKey();
        if (mediaId == null) return;
        String mime = getContentResolver().getType(uri);
        String type = (mime != null && mime.startsWith("video")) ? "video" : "image";

        CloudinaryUploader.upload(this, uri, "highlights", new CloudinaryUploader.Callback() {
            @Override public void onProgress(int percent) {}

            @Override
            public void onSuccess(String secureUrl) {
                HighlightMediaModel m = new HighlightMediaModel(
                        secureUrl, type, System.currentTimeMillis());
                m.setMediaId(mediaId);
                FirebaseDatabase.getInstance()
                        .getReference("highlights").child(highlightId)
                        .child("media").child(mediaId)
                        .setValue(m)
                        .addOnSuccessListener(u -> {
                            mediaList.add(m);
                            Collections.sort(mediaList,
                                    (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
                            buildProgressBars();
                            showMedia(mediaList.size() - 1);
                        });
            }

            @Override
            public void onFailure(String error) {
                android.widget.Toast.makeText(HighlightViewerActivity.this,
                        "Upload failed. Please try again.",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Teacher: delete current media ────────────────────────────────────
    private void confirmDeleteMedia() {
        if (mediaList.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete Media")
                .setMessage("Remove this item from the highlight?")
                .setPositiveButton("Delete", (d, w) -> deleteCurrentMedia())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCurrentMedia() {
        HighlightMediaModel m = mediaList.get(currentIndex);
        cancelTimers();
        FirebaseDatabase.getInstance()
                .getReference("highlights").child(highlightId)
                .child("media").child(m.getMediaId())
                .removeValue();
        mediaList.remove(currentIndex);
        if (mediaList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            llProgressBars.removeAllViews();
            ivImage.setVisibility(View.GONE);
            vvVideo.setVisibility(View.GONE);
        } else {
            buildProgressBars();
            showMedia(Math.min(currentIndex, mediaList.size() - 1));
        }
    }
}
