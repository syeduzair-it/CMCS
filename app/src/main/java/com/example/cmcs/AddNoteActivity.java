package com.example.cmcs;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cmcs.utils.CloudinaryUploader;
import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Objects;

/**
 * AddNoteActivity — teacher only.
 *
 * Fields: Title (required), File (PDF / image / video, required). Upload flow:
 * 1. push() key from DB → noteId 2. Upload to Cloudinary (folder: notes) 3. Get
 * secure_url 4. Write NoteModel to DB
 */
public class AddNoteActivity extends AppCompatActivity {

    private TextInputEditText etTitle;
    private MaterialButton btnPickFile, btnUpload;
    private TextView tvPickedFile;
    private LinearLayout llProgress;

    private String dept, course, year, subject, uid;
    private Uri pickedUri;
    private String fileType; // "pdf" | "image" | "video"

    private final ActivityResultLauncher<String> filePicker
            = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }
                pickedUri = uri;
                String mime = getContentResolver().getType(uri);
                if (mime == null) {
                    showUnsupported();
                    return;
                }

                if (mime.startsWith("image/")) {
                    fileType = "image";
                } else if (mime.equals("application/pdf")) {
                    fileType = "pdf";
                } else if (mime.startsWith("video/")) {
                    fileType = "video";
                } else {
                    showUnsupported();
                    return;
                }

                String segment = uri.getLastPathSegment();
                tvPickedFile.setText(segment != null ? segment : "File selected");
                tvPickedFile.setVisibility(View.VISIBLE);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_note);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        Intent i = getIntent();
        dept = i.getStringExtra(NotesClassActivity.EXTRA_DEPT);
        course = i.getStringExtra(NotesClassActivity.EXTRA_COURSE);
        year = i.getStringExtra(NotesClassActivity.EXTRA_YEAR);
        subject = i.getStringExtra("subject");
        uid = i.getStringExtra("uid");

        etTitle = findViewById(R.id.et_note_title);
        btnPickFile = findViewById(R.id.btn_pick_file);
        btnUpload = findViewById(R.id.btn_upload_note);
        tvPickedFile = findViewById(R.id.tv_picked_file);
        llProgress = findViewById(R.id.ll_upload_progress);

        MaterialToolbar toolbar = findViewById(R.id.an_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        btnPickFile.setOnClickListener(v -> filePicker.launch("*/*"));
        btnUpload.setOnClickListener(v -> upload());
    }

    private void upload() {
        String title = Objects.requireNonNull(etTitle.getText()).toString().trim();
        if (TextUtils.isEmpty(title)) {
            etTitle.setError("Title is required");
            return;
        }
        if (pickedUri == null) {
            Toast.makeText(this, "Please pick a file first", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true);

        android.util.Log.d("PATH_DEBUG", dept + "/" + NotesClassActivity.normalizeCourse(course) + "/" + year);

        // Build DB reference for this subject
        DatabaseReference subjectRef = FirebaseDatabase.getInstance()
                .getReference("notes")
                .child(NotesClassActivity.sanitize(dept))
                .child(NotesClassActivity.normalizeCourse(course))
                .child(NotesClassActivity.sanitize(year))
                .child(subject != null ? subject : "_");

        // Generate unique noteId
        String noteId = subjectRef.push().getKey();
        if (noteId == null) {
            Toast.makeText(this, "DB error — try again", Toast.LENGTH_SHORT).show();
            setBusy(false);
            return;
        }

        CloudinaryUploader.upload(this, pickedUri, "notes", new CloudinaryUploader.Callback() {
            @Override
            public void onProgress(int percent) {
                // Progress bar is indeterminate; no-op for now
            }

            @Override
            public void onSuccess(String secureUrl) {
                // Write metadata to DB
                long now = System.currentTimeMillis();
                com.example.cmcs.models.NoteModel note
                        = new com.example.cmcs.models.NoteModel(
                                noteId, title,
                                secureUrl,
                                fileType,
                                uid,
                                "Teacher",
                                now);
                subjectRef.child(noteId).setValue(note)
                        .addOnSuccessListener(v -> {
                            setBusy(false);
                            Toast.makeText(AddNoteActivity.this,
                                    "Note uploaded", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            setBusy(false);
                            Toast.makeText(AddNoteActivity.this,
                                    "DB save failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
            }

            @Override
            public void onFailure(String error) {
                setBusy(false);
                Toast.makeText(AddNoteActivity.this,
                        "Upload failed. Please try again.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setBusy(boolean busy) {
        btnUpload.setEnabled(!busy);
        btnPickFile.setEnabled(!busy);
        llProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void showUnsupported() {
        pickedUri = null;
        fileType = null;
        tvPickedFile.setVisibility(View.GONE);
        Toast.makeText(this,
                "Unsupported file type. Use PDF, image, or video.",
                Toast.LENGTH_SHORT).show();
    }
}
