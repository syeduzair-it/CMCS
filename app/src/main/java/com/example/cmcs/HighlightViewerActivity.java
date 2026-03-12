package com.example.cmcs;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
 * HighlightViewerActivity — Full-screen highlight media viewer.
 *
 * Loads all media from highlights/<highlightId>/media, sorted by timestamp.
 * Images: slide-show with 5-second timer + Instagram progress bars. Videos:
 * play until completion, progress bar polled at 100 ms. Left/right tap zones
 * for navigation. Teachers can add media to an existing highlight or delete the
 * current item.
 *
 * Media is uploaded to Cloudinary (folder: highlights). On delete the DB record
 * is removed; the Cloudinary asset is orphaned (client-side deletion requires
 * an API secret and must happen server-side).
 */
public class HighlightViewerActivity extends AppCompatActivity {

    public static final String EXTRA_HIGHLIGHT_ID = "highlight_id";
    public static final String EXTRA_HIGHLIGHT_TITLE = "highlight_title";
    public static final String EXTRA_IS_TEACHER = "is_teacher";
    public static final String EXTRA_CREATED_BY_UID = "created_by_uid";

    private static final long IMAGE_DURATION_MS = 5_000L;
    private static final long VIDEO_POLL_MS = 100L;

    private ImageView ivImage;
    private VideoView vvVideo;
    private LinearLayout llProgressBars;
    private TextView tvTitle, tvEmpty;
    private ImageButton btnAddMedia, btnDeleteMedia;

    private final List<HighlightMediaModel> mediaList = new ArrayList<>();
    private int currentIndex;
    private String highlightId;
    private boolean isTeacher;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable advanceRunnable;
    private Runnable videoProgressRunnable;
    private ProgressBar[] bars = new ProgressBar[0];

    private final ActivityResultLauncher<String> mediaPicker
            = registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            uploadAdditionalMedia(uri);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_highlight_viewer);

        ivImage = findViewById(R.id.hv_ivImage);
        vvVideo = findViewById(R.id.hv_vvVideo);
        llProgressBars = findViewById(R.id.hv_llProgressBars);
        tvTitle = findViewById(R.id.hv_tvTitle);
        tvEmpty = findViewById(R.id.hv_tvEmpty);
        btnAddMedia = findViewById(R.id.hv_btnAddMedia);
        btnDeleteMedia = findViewById(R.id.hv_btnDeleteMedia);

        highlightId = getIntent().getStringExtra(EXTRA_HIGHLIGHT_ID);
        isTeacher = getIntent().getBooleanExtra(EXTRA_IS_TEACHER, false);
        tvTitle.setText(getIntent().getStringExtra(EXTRA_HIGHLIGHT_TITLE));

        findViewById(R.id.hv_btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.hv_tapLeft).setOnClickListener(v -> moveTo(currentIndex - 1));
        findViewById(R.id.hv_tapRight).setOnClickListener(v -> moveTo(currentIndex + 1));

        if (isTeacher) {
            btnAddMedia.setVisibility(View.VISIBLE);
            btnDeleteMedia.setVisibility(View.VISIBLE);
            btnAddMedia.setOnClickListener(v -> mediaPicker.launch("image/* video/*"));
            btnDeleteMedia.setOnClickListener(v -> confirmDeleteMedia());
        }

        loadMedia();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimers();
        if (vvVideo.isPlaying()) {
            vvVideo.stopPlayback();
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
                            if (m == null) {
                                continue;
                            }
                            m.setMediaId(child.getKey());
                            mediaList.add(m);
                        }
                        Collections.sort(mediaList,
                                (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
                        if (mediaList.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                        } else {
                            tvEmpty.setVisibility(View.GONE);
                            buildProgressBars();
                            showMedia(0);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                    }
                });
    }

    // ── Progress bars ─────────────────────────────────────────────────────
    private void buildProgressBars() {
        llProgressBars.removeAllViews();
        bars = new ProgressBar[mediaList.size()];
        for (int i = 0; i < mediaList.size(); i++) {
            ProgressBar pb = new ProgressBar(this, null,
                    android.R.attr.progressBarStyleHorizontal);
            pb.setMax(1000);
            pb.setProgress(0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 4, 1f);
            lp.setMarginStart(i == 0 ? 0 : 4);
            pb.setLayoutParams(lp);
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x80FFFFFF));
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
        if (index < 0 || index >= mediaList.size()) {
            finish();
            return;
        }
        currentIndex = index;
        cancelTimers();
        syncProgressBars();
        HighlightMediaModel m = mediaList.get(index);
        if ("video".equals(m.getMediaType())) {
            showVideo(m);
        } else {
            showImage(m);
        }
    }

    private void showImage(HighlightMediaModel m) {
        vvVideo.setVisibility(View.GONE);
        ivImage.setVisibility(View.VISIBLE);
        Glide.with(this).load(m.getMediaUrl()).thumbnail(0.3f).into(ivImage);

        final ProgressBar pb = bars[currentIndex];
        final long startMs = System.currentTimeMillis();
        final int capIdx = currentIndex;
        Runnable ticker = new Runnable() {
            @Override
            public void run() {
                if (currentIndex != capIdx) {
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

    private void showVideo(HighlightMediaModel m) {
        ivImage.setVisibility(View.GONE);
        vvVideo.setVisibility(View.VISIBLE);
        vvVideo.setVideoURI(Uri.parse(m.getMediaUrl()));
        vvVideo.requestFocus();
        vvVideo.start();
        vvVideo.setOnCompletionListener(mp -> moveTo(currentIndex + 1));

        final ProgressBar pb = bars[currentIndex];
        final int capIdx = currentIndex;
        videoProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentIndex != capIdx || !vvVideo.isPlaying()) {
                    return;
                }
                int dur = vvVideo.getDuration();
                if (dur > 0) {
                    pb.setProgress((int) (1000L * vvVideo.getCurrentPosition() / dur));
                }
                handler.postDelayed(this, VIDEO_POLL_MS);
            }
        };
        vvVideo.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            handler.post(videoProgressRunnable);
        });
    }

    private void moveTo(int index) {
        if (index < 0) {
            showMedia(currentIndex);
            return;
        }
        if (index >= mediaList.size()) {
            finish();
            return;
        }
        showMedia(index);
    }

    private void cancelTimers() {
        if (advanceRunnable != null) {
            handler.removeCallbacks(advanceRunnable);
        }
        if (videoProgressRunnable != null) {
            handler.removeCallbacks(videoProgressRunnable);
        }
        advanceRunnable = videoProgressRunnable = null;
    }

    // ── Teacher: add media to existing highlight ─────────────────────────
    private void uploadAdditionalMedia(Uri uri) {
        String mediaId = FirebaseDatabase.getInstance().getReference().push().getKey();
        if (mediaId == null) {
            return;
        }
        String mime = getContentResolver().getType(uri);
        String type = (mime != null && mime.startsWith("video")) ? "video" : "image";

        CloudinaryUploader.upload(this, uri, "highlights", new CloudinaryUploader.Callback() {
            @Override
            public void onProgress(int percent) {
                // no dedicated progress bar in this screen; no-op
            }

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
                        "Upload failed. Please try again.", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Teacher: delete current media item ───────────────────────────────
    private void confirmDeleteMedia() {
        if (mediaList.isEmpty()) {
            return;
        }
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

        // Remove DB record
        FirebaseDatabase.getInstance()
                .getReference("highlights").child(highlightId)
                .child("media").child(m.getMediaId())
                .removeValue();

        // Note: Cloudinary asset is orphaned — client-side deletion requires
        // an API secret and should be handled server-side (e.g. Cloud Function).
        mediaList.remove(currentIndex);
        if (mediaList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            llProgressBars.removeAllViews();
            ivImage.setVisibility(View.GONE);
            vvVideo.setVisibility(View.GONE);
        } else {
            buildProgressBars();
            showMedia(Math.min(currentIndex, mediaList.size() - 1));
        }
    }
}
