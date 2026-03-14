package com.example.cmcs;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/**
 * Full-screen in-app video player for notice videos.
 *
 * Extras:
 *   EXTRA_VIDEO_URL — Cloudinary video URL
 */
public class NoticeVideoViewerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URL = "video_url";

    private ExoPlayer    player;
    private ProgressBar  progressBuffering;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice_video_viewer);

        String videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);

        MaterialToolbar toolbar = findViewById(R.id.toolbarVideoViewer);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        PlayerView      playerView    = findViewById(R.id.playerView);
        progressBuffering             = findViewById(R.id.progressBuffering);
        MaterialButton  btnDownload   = findViewById(R.id.btnDownloadVideo);
        MaterialButton  btnShare      = findViewById(R.id.btnShareVideo);

        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "Video not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ── ExoPlayer setup ───────────────────────────────────────────────
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                // Hide spinner once player is ready or ended
                if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                    progressBuffering.setVisibility(View.GONE);
                } else if (state == Player.STATE_BUFFERING) {
                    progressBuffering.setVisibility(View.VISIBLE);
                }
            }
        });

        player.setMediaItem(MediaItem.fromUri(videoUrl));
        player.prepare();
        player.play();

        // ── Download ──────────────────────────────────────────────────────
        btnDownload.setOnClickListener(v -> startDownload(videoUrl));

        // ── Share ─────────────────────────────────────────────────────────
        btnShare.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, videoUrl);
            startActivity(Intent.createChooser(share, "Share video via"));
        });
    }

    // ── Lifecycle — pause/resume/release ─────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) player.play();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    // ── Download via DownloadManager ──────────────────────────────────────
    private void startDownload(String url) {
        try {
            String fileName = "CMCS_video_" + System.currentTimeMillis() + ".mp4";
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle(fileName)
                    .setDescription("Downloading notice video")
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS, "CMCS/" + fileName)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true);

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(request);
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
