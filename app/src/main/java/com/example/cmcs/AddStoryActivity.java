package com.example.cmcs;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.cmcs.models.StoryModel;
import com.example.cmcs.utils.CloudinaryUploader;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * AddStoryActivity — Teachers only.
 *
 * Lets the teacher pick an image or video, uploads it to Cloudinary (folder:
 * stories) and writes a StoryModel to the database.
 *
 * Database: stories/college/<storyId>
 * stories/teachers/<uid>/<storyId>
 */
public class AddStoryActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "story_type";  // "college" | "teacher"

    private ImageView ivPreview;
    private ProgressBar loading;
    private MaterialButton btnUpload;

    private Uri selectedUri;
    private String mimeType;   // "image" or "video" derived from picked URI
    private String storyType;  // "college" | "teacher"
    private String teacherUid;
    private String teacherName;

    // ── Gallery / video picker ────────────────────────────────────────────
    private final ActivityResultLauncher<String> mediaPicker
            = registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            selectedUri = uri;
                            mimeType = resolveMimeType(uri);
                            Glide.with(this)
                                    .load(uri)
                                    .thumbnail(0.3f)
                                    .centerCrop()
                                    .into(ivPreview);
                            ivPreview.setVisibility(View.VISIBLE);
                            btnUpload.setEnabled(true);
                        }
                    });

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_story);

        storyType = getIntent().getStringExtra(EXTRA_TYPE);
        if (storyType == null) {
            storyType = "teacher";
        }

        ivPreview = findViewById(R.id.ivPreview);
        loading = findViewById(R.id.as_loading);
        btnUpload = findViewById(R.id.btnUpload);

        MaterialToolbar toolbar = findViewById(R.id.as_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle("college".equals(storyType)
                ? "Add College Story" : "Add Your Story");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        teacherUid = user.getUid();

        // Resolve teacher name from users/ node
        FirebaseDatabase.getInstance().getReference("users").child(teacherUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot s) {
                        teacherName = s.child("name").getValue(String.class);
                        if (teacherName == null) {
                            teacherName = "Teacher";
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                    }
                });

        findViewById(R.id.btnPickMedia).setOnClickListener(
                v -> mediaPicker.launch("image/* video/*"));

        btnUpload.setOnClickListener(v -> uploadStory());
    }

    // ── Upload flow ───────────────────────────────────────────────────────
    private void uploadStory() {
        if (selectedUri == null) {
            return;
        }

        setLoading(true);

        // Generate unique story ID upfront (DB push key)
        String storyId = FirebaseDatabase.getInstance()
                .getReference("stories").push().getKey();
        if (storyId == null) {
            setLoading(false);
            return;
        }

        CloudinaryUploader.upload(this, selectedUri, "stories", new CloudinaryUploader.Callback() {
            @Override
            public void onProgress(int percent) {
                // loading spinner already visible; no-op
            }

            @Override
            public void onSuccess(String secureUrl) {
                saveToDatabase(storyId, secureUrl);
            }

            @Override
            public void onFailure(String error) {
                onUploadError(error);
            }
        });
    }

    private void saveToDatabase(String storyId, String downloadUrl) {
        String type = storyType;                    // "college" | "teacher"
        String dbPath = "college".equals(type)
                ? "stories/college/" + storyId
                : "stories/teachers/" + teacherUid + "/" + storyId;

        StoryModel model = new StoryModel(
                storyId, downloadUrl, mimeType,
                teacherUid, teacherName,
                System.currentTimeMillis(), type);

        FirebaseDatabase.getInstance().getReference(dbPath)
                .setValue(model)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    Toast.makeText(this, "Story uploaded!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> onUploadError(e.getMessage()));
    }

    private void onUploadError(String msg) {
        setLoading(false);
        Toast.makeText(this, "Upload failed. Please try again.", Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean on) {
        loading.setVisibility(on ? View.VISIBLE : View.GONE);
        btnUpload.setEnabled(!on);
        findViewById(R.id.btnPickMedia).setEnabled(!on);
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    /**
     * Derives "image" or "video" from the URI's MIME type.
     */
    private String resolveMimeType(Uri uri) {
        String mime = getContentResolver().getType(uri);
        if (mime != null && mime.startsWith("video")) {
            return "video";
        }
        return "image";
    }
}
