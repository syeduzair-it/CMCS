package com.example.cmcs;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.File;

/**
 * Full-screen viewer for notice media (image or PDF).
 * Video is handled by the external player directly from NoticeAdapter.
 *
 * Extras:
 *   EXTRA_MEDIA_URL  — download URL string
 *   EXTRA_MEDIA_TYPE — "image" | "pdf"
 */
public class NoticeMediaViewerActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_URL  = "media_url";
    public static final String EXTRA_MEDIA_TYPE = "media_type";

    private long downloadId = -1;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == downloadId) {
                Toast.makeText(context, "Download complete", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice_media_viewer);

        String mediaUrl  = getIntent().getStringExtra(EXTRA_MEDIA_URL);
        String mediaType = getIntent().getStringExtra(EXTRA_MEDIA_TYPE);

        MaterialToolbar toolbar = findViewById(R.id.toolbarMediaViewer);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ImageView      ivFull          = findViewById(R.id.ivFullImage);
        LinearLayout   llPdf           = findViewById(R.id.llPdfPlaceholder);
        MaterialButton btnOpenPdf      = findViewById(R.id.btnOpenPdf);
        MaterialButton btnDownload     = findViewById(R.id.btnDownload);
        MaterialButton btnShare        = findViewById(R.id.btnShare);

        if (mediaUrl == null || mediaUrl.isEmpty()) {
            Toast.makeText(this, "Media not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if ("image".equals(mediaType)) {
            toolbar.setTitle("Image");
            ivFull.setVisibility(android.view.View.VISIBLE);
            llPdf.setVisibility(android.view.View.GONE);
            Glide.with(this).load(mediaUrl).fitCenter()
                    .placeholder(android.R.color.black).into(ivFull);
        } else {
            // PDF — show icon + "Open" button; use correct MIME so Android
            // offers Drive / Adobe / WPS etc. instead of a browser download.
            toolbar.setTitle("PDF Document");
            ivFull.setVisibility(android.view.View.GONE);
            llPdf.setVisibility(android.view.View.VISIBLE);
            btnOpenPdf.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(mediaUrl), "application/pdf");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                    startActivity(Intent.createChooser(intent, "Open PDF with"));
                } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(this,
                            "No PDF viewer installed. Please install Adobe Acrobat or similar.",
                            Toast.LENGTH_LONG).show();
                }
            });
        }

        // ── Download ──────────────────────────────────────────────────────
        final String finalUrl = mediaUrl;
        final String finalType = mediaType;
        btnDownload.setOnClickListener(v -> startDownload(finalUrl, finalType));

        // ── Share ─────────────────────────────────────────────────────────
        btnShare.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, finalUrl);
            startActivity(Intent.createChooser(share, "Share via"));
        });
    }

    private void startDownload(String url, String type) {
        try {
            String ext      = "pdf".equals(type) ? ".pdf" : ".jpg";
            String fileName = "CMCS_notice_" + System.currentTimeMillis() + ext;

            // Ensure Downloads/CMCS/ directory exists
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "CMCS");
            if (!dir.exists()) dir.mkdirs();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle(fileName)
                    .setDescription("Downloading notice media")
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS, "CMCS/" + fileName)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true);

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            downloadId = dm.enqueue(request);
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(downloadReceiver);
    }
}
