package com.example.cmcs;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.cmcs.models.NoticeModel;
import com.example.cmcs.utils.CloudinaryUploader;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.FirebaseDatabase;

import android.util.Log;
import java.util.Objects;

/**
 * Activity for creating or editing a notice.
 *
 * Intent extras: EXTRA_MODE — "class" | "college" | "training" EXTRA_DEPARTMENT
 * — department string EXTRA_COURSE — course (class mode) EXTRA_YEAR — year
 * (class mode) EXTRA_CREATOR_UID — teacher's UID EXTRA_CREATOR_NAME — teacher's
 * display name
 *
 * Edit mode extras (in addition to above): EXTRA_EDIT_MODE — true
 * EXTRA_NOTICE_ID — existing ID EXTRA_TITLE — existing title EXTRA_DESCRIPTION
 * — existing description EXTRA_MEDIA_TYPE — existing media type EXTRA_MEDIA_URL
 * — existing media URL EXTRA_DB_PATH — existing DB path (to update correct
 * node)
 *
 * On edit with new media, the old Cloudinary URL is orphaned (client-side
 * deletion requires an API secret and must happen server-side).
 */
public class AddNoticeActivity extends AppCompatActivity {

    // Intent extra keys
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_DEPARTMENT = "dept";
    public static final String EXTRA_COURSE = "course";
    public static final String EXTRA_YEAR = "year";
    public static final String EXTRA_CREATOR_UID = "creator_uid";
    public static final String EXTRA_CREATOR_NAME = "creator_name";
    public static final String EXTRA_EDIT_MODE = "edit_mode";
    public static final String EXTRA_NOTICE_ID = "notice_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_DESCRIPTION = "description";
    public static final String EXTRA_MEDIA_TYPE = "media_type";
    public static final String EXTRA_MEDIA_URL = "media_url";
    public static final String EXTRA_DB_PATH = "db_path";

    // UI
    private TextInputEditText etTitle, etDescription;
    private MaterialButton btnPickMedia, btnSaveNotice;
    private ImageButton btnRemoveMedia;
    private TextView tvMediaName;
    private FrameLayout mediaPreview;
    private ImageView ivPreview, ivMediaTypeIcon;
    private LinearLayout nonImagePreview, uploadProgressLayout;
    private TextView tvMediaTypeLabel;

    // State
    private boolean isEditMode = false;
    private String noticeId;
    private String mode;
    private String department;
    private String course;
    private String year;
    private String creatorUid;
    private String creatorName;
    private String dbPath;

    private Uri pickedMediaUri;
    private String pickedMimeType;

    private final ActivityResultLauncher<String> mediaPicker
            = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }
                pickedMediaUri = uri;
                String mime = getContentResolver().getType(uri);
                if (mime != null && mime.startsWith("image/")) {
                    pickedMimeType = "image";
                } else if (mime != null && mime.equals("application/pdf")) {
                    pickedMimeType = "pdf";
                } else if (mime != null && mime.startsWith("video/")) {
                    pickedMimeType = "video";
                } else {
                    pickedMimeType = "text";
                    pickedMediaUri = null;
                    Toast.makeText(this, "Unsupported file type", Toast.LENGTH_SHORT).show();
                    return;
                }
                showPickedMediaPreview();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_notice);

        bindViews();
        readIntent();
        setupToolbar();
        setupClickListeners();
    }

    // ── Init ──────────────────────────────────────────────────────────────
    private void bindViews() {
        etTitle = findViewById(R.id.etNoticeTitle);
        etDescription = findViewById(R.id.etNoticeDescription);
        btnPickMedia = findViewById(R.id.btnPickMedia);
        btnSaveNotice = findViewById(R.id.btnSaveNotice);
        btnRemoveMedia = findViewById(R.id.btnRemoveMedia);
        tvMediaName = findViewById(R.id.tvMediaName);
        mediaPreview = findViewById(R.id.mediaPreview);
        ivPreview = findViewById(R.id.ivPreview);
        ivMediaTypeIcon = findViewById(R.id.ivMediaTypeIcon);
        nonImagePreview = findViewById(R.id.nonImagePreview);
        uploadProgressLayout = findViewById(R.id.uploadProgressLayout);
        tvMediaTypeLabel = findViewById(R.id.tvMediaTypeLabel);
    }

    private void readIntent() {
        Intent i = getIntent();
        isEditMode = i.getBooleanExtra(EXTRA_EDIT_MODE, false);

        if (isEditMode) {
            noticeId = i.getStringExtra(EXTRA_NOTICE_ID);
            dbPath = i.getStringExtra(EXTRA_DB_PATH);

            Log.d("NOTICE_EDIT", "dbPath=" + dbPath);
            Log.d("NOTICE_EDIT", "noticeId=" + noticeId);

            if (dbPath == null || dbPath.isEmpty()) {
                Toast.makeText(this,
                        "Edit error: DB path missing — please try again",
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            String prefillTitle = i.getStringExtra(EXTRA_TITLE);
            String prefillDesc = i.getStringExtra(EXTRA_DESCRIPTION);
            if (prefillTitle != null) {
                etTitle.setText(prefillTitle);
            }
            if (prefillDesc != null) {
                etDescription.setText(prefillDesc);
            }

        } else {
            mode = i.getStringExtra(EXTRA_MODE);
            department = i.getStringExtra(EXTRA_DEPARTMENT);
            course = i.getStringExtra(EXTRA_COURSE);
            year = i.getStringExtra(EXTRA_YEAR);
            creatorUid = i.getStringExtra(EXTRA_CREATOR_UID);
            creatorName = i.getStringExtra(EXTRA_CREATOR_NAME);
            dbPath = buildDbPath(mode, department, course, year);
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.addNoticeToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isEditMode ? "Edit Notice" : "New Notice");
        }
    }

    private void setupClickListeners() {
        btnPickMedia.setOnClickListener(v -> mediaPicker.launch("*/*"));
        btnRemoveMedia.setOnClickListener(v -> clearMediaSelection());
        btnSaveNotice.setOnClickListener(v -> saveNotice());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Media Helpers ─────────────────────────────────────────────────────
    private void showPickedMediaPreview() {
        mediaPreview.setVisibility(View.VISIBLE);
        btnRemoveMedia.setVisibility(View.VISIBLE);
        tvMediaName.setText(pickedMediaUri.getLastPathSegment());

        if ("image".equals(pickedMimeType)) {
            ivPreview.setVisibility(View.VISIBLE);
            nonImagePreview.setVisibility(View.GONE);
            Glide.with(this).load(pickedMediaUri).centerCrop().into(ivPreview);
        } else {
            ivPreview.setVisibility(View.GONE);
            nonImagePreview.setVisibility(View.VISIBLE);
            ivMediaTypeIcon.setImageResource(
                    "pdf".equals(pickedMimeType) ? R.drawable.ic_pdf : R.drawable.ic_video);
            tvMediaTypeLabel.setText("pdf".equals(pickedMimeType) ? "PDF Selected" : "Video Selected");
        }
    }

    private void clearMediaSelection() {
        pickedMediaUri = null;
        pickedMimeType = null;
        mediaPreview.setVisibility(View.GONE);
        btnRemoveMedia.setVisibility(View.GONE);
        tvMediaName.setText("");
    }

    // ── Save Logic ────────────────────────────────────────────────────────
    private void saveNotice() {
        String title = Objects.requireNonNull(etTitle.getText()).toString().trim();
        String desc  = Objects.requireNonNull(etDescription.getText()).toString().trim();

        // A notice is valid if it has any content — text OR media.
        if (title.isEmpty() && desc.isEmpty() && pickedMediaUri == null) {
            Toast.makeText(this, "Add text or attach a file", Toast.LENGTH_SHORT).show();
            return;
        }

        // 50 MB video size guard
        if ("video".equals(pickedMimeType) && pickedMediaUri != null) {
            long sizeBytes = getFileSizeBytes(pickedMediaUri);
            if (sizeBytes > 50 * 1024 * 1024L) {
                Toast.makeText(this,
                        "Video exceeds 50 MB limit. Please trim or compress it first.",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        setBusy(true);

        if (isEditMode) {
            saveEdit(title, desc);
        } else {
            noticeId = FirebaseDatabase.getInstance().getReference(dbPath).push().getKey();
            if (pickedMediaUri != null) {
                uploadMediaThenSave(title, desc);
            } else {
                writeNotice(title, desc, "text", null);
            }
        }
    }

    /** Returns file size in bytes, or 0 if it cannot be determined. */
    private long getFileSizeBytes(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.SIZE},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx);
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private void saveEdit(String title, String desc) {
        if (pickedMediaUri != null) {
            // Old Cloudinary URL is orphaned — deletion requires server-side API secret
            uploadMediaThenSave(title, desc);
        } else {
            // No new media — just update text fields
            FirebaseDatabase.getInstance()
                    .getReference(dbPath)
                    .child(noticeId)
                    .updateChildren(new java.util.HashMap<String, Object>() {
                        {
                            put("title", title);
                            put("description", desc);
                            put("edited", true);
                        }
                    })
                    .addOnSuccessListener(v -> {
                        setBusy(false);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        setBusy(false);
                        toast("Save failed: " + e.getMessage());
                    });
        }
    }

    private void uploadMediaThenSave(String title, String desc) {
        if (noticeId == null) {
            toast("Error: noticeId is null");
            setBusy(false);
            return;
        }

        uploadProgressLayout.setVisibility(View.VISIBLE);

        CloudinaryUploader.upload(this, pickedMediaUri, "notices", new CloudinaryUploader.Callback() {
            @Override
            public void onProgress(int percent) {
                // progress bar is already visible (indeterminate); no-op
            }

            @Override
            public void onSuccess(String secureUrl) {
                uploadProgressLayout.setVisibility(View.GONE);
                writeNotice(title, desc, pickedMimeType, secureUrl);
            }

            @Override
            public void onFailure(String error) {
                setBusy(false);
                uploadProgressLayout.setVisibility(View.GONE);
                toast("Upload failed. Please try again.");
            }
        });
    }

    private void writeNotice(String title, String desc, String mediaType, String mediaUrl) {
        long timestamp = System.currentTimeMillis();
        NoticeModel notice = new NoticeModel(
                noticeId, title, desc, mediaType,
                mediaUrl != null ? mediaUrl : "",
                creatorUid, creatorName,
                department,
                course != null ? course : "",
                year != null ? year : "",
                timestamp,
                isEditMode
        );

        FirebaseDatabase.getInstance()
                .getReference(dbPath)
                .child(noticeId)
                .setValue(notice)
                .addOnSuccessListener(v -> {
                    setBusy(false);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    toast("Failed: " + e.getMessage());
                });
    }

    // ── Utilities ─────────────────────────────────────────────────────────
    private static String buildDbPath(String mode, String dept, String course, String year) {
        switch (mode != null ? mode : "") {
            case "class":
                return "notices/class/"
                        + sanitize(dept) + "/"
                        + sanitize(course) + "/"
                        + sanitize(year);
            case "training":
                return "notices/training";
            default: // "college"
                return "notices/college";
        }
    }

    private static String sanitize(String s) {
        if (s == null) {
            return "_";
        }
        return s.trim().replace(" ", "_").replace(".", "_")
                .replace("#", "_").replace("$", "_")
                .replace("[", "_").replace("]", "_");
    }

    private void setBusy(boolean busy) {
        btnSaveNotice.setEnabled(!busy);
        btnPickMedia.setEnabled(!busy);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
