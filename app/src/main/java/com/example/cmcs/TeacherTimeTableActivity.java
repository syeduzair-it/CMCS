package com.example.cmcs;

import android.app.AlertDialog;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.github.chrisbanes.photoview.PhotoView;
import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TeacherTimeTableActivity — Phase 5
 *
 * Teacher-only screen that manages one timetable image stored locally at:
 * getFilesDir()/timetable/timetable.jpg
 *
 * States: • No image → empty-state layout + "Upload" button • Image exists →
 * PhotoView (zoomable) + Replace / Delete buttons
 *
 * File copy runs on a background executor (no ANR, no storage permission
 * needed). configChanges="orientation|screenSize" on the manifest entry
 * prevents recreation while the PhotoView scale state is maintained.
 */
public class TeacherTimeTableActivity extends AppCompatActivity {

    // ── File path ─────────────────────────────────────────────────────────
    private File timetableFile;

    // ── Views ─────────────────────────────────────────────────────────────
    private ProgressBar loadingSpinner;
    private LinearLayout llEmptyState;
    private PhotoView photoView;
    private LinearLayout llActionBar;

    // ── Background work ───────────────────────────────────────────────────
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ── Image picker ──────────────────────────────────────────────────────
    private final ActivityResultLauncher<String> imagePicker
            = registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            copyAndLoad(uri);
                        }
                    });

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_timetable);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        timetableFile = new File(getFilesDir(), "timetable/timetable.jpg");

        bindViews();
        setupToolbar();
        setupButtons();

        refreshUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────
    // View binding
    // ─────────────────────────────────────────────────────────────────────
    private void bindViews() {
        loadingSpinner = findViewById(R.id.tt_loading);
        llEmptyState = findViewById(R.id.ll_empty_state);
        photoView = findViewById(R.id.photo_view_timetable);
        llActionBar = findViewById(R.id.ll_action_bar);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.tt_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupButtons() {
        MaterialButton btnUpload = findViewById(R.id.btn_upload);
        MaterialButton btnReplace = findViewById(R.id.btn_replace);
        MaterialButton btnDelete = findViewById(R.id.btn_delete);

        btnUpload.setOnClickListener(v -> openPicker());
        btnReplace.setOnClickListener(v -> openPicker());
        btnDelete.setOnClickListener(v -> confirmDelete());
    }

    // ─────────────────────────────────────────────────────────────────────
    // UI state
    // ─────────────────────────────────────────────────────────────────────
    /**
     * Inspect timetableFile and switch between empty-state and image-state.
     * Must be called on the main thread.
     */
    private void refreshUI() {
        if (timetableFile.exists()) {
            showImage();
        } else {
            showEmptyState();
        }
    }

    private void showEmptyState() {
        photoView.setVisibility(View.GONE);
        llActionBar.setVisibility(View.GONE);
        llEmptyState.setVisibility(View.VISIBLE);
    }

    private void showImage() {
        llEmptyState.setVisibility(View.GONE);
        // Decode on main thread is safe here: image is small (local JPEG)
        photoView.setImageBitmap(
                BitmapFactory.decodeFile(timetableFile.getAbsolutePath()));
        photoView.setVisibility(View.VISIBLE);
        llActionBar.setVisibility(View.VISIBLE);
    }

    // ─────────────────────────────────────────────────────────────────────
    // File picker
    // ─────────────────────────────────────────────────────────────────────
    private void openPicker() {
        imagePicker.launch("image/*");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Copy → load
    // ─────────────────────────────────────────────────────────────────────
    /**
     * Copy picked image URI into internal storage on a background thread.
     */
    private void copyAndLoad(Uri uri) {
        loadingSpinner.setVisibility(View.VISIBLE);
        llEmptyState.setVisibility(View.GONE);
        photoView.setVisibility(View.GONE);
        llActionBar.setVisibility(View.GONE);

        executor.execute(() -> {
            boolean success = false;
            try {
                // Ensure directory exists
                File dir = timetableFile.getParentFile();
                if (dir != null && !dir.exists()) //noinspection ResultOfMethodCallIgnored
                {
                    dir.mkdirs();
                }

                // Stream copy (overwrites if replacing)
                try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(timetableFile)) {
                    if (in == null) {
                        throw new IOException("Cannot open URI");
                    }
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        out.write(buf, 0, read);
                    }
                }
                success = true;
            } catch (IOException e) {
                e.printStackTrace();
            }

            final boolean ok = success;
            runOnUiThread(() -> {
                loadingSpinner.setVisibility(View.GONE);
                if (ok) {
                    showImage();
                    Toast.makeText(this,
                            "Time table saved", Toast.LENGTH_SHORT).show();
                } else {
                    refreshUI();
                    Toast.makeText(this,
                            "Failed to save image", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Delete
    // ─────────────────────────────────────────────────────────────────────
    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Time Table")
                .setMessage("Are you sure you want to remove the saved time table?")
                .setPositiveButton("Delete", (d, w) -> deleteImage())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteImage() {
        executor.execute(() -> {
            if (timetableFile.exists()) //noinspection ResultOfMethodCallIgnored
            {
                timetableFile.delete();
            }
            runOnUiThread(() -> {
                photoView.setImageBitmap(null);
                showEmptyState();
                Toast.makeText(this,
                        "Time table deleted", Toast.LENGTH_SHORT).show();
            });
        });
    }
}
