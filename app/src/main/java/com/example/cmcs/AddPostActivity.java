package com.example.cmcs;

import android.app.ProgressDialog;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.cmcs.models.PostModel;
import com.example.cmcs.utils.CloudinaryUploader;
import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AddPostActivity extends AppCompatActivity {

    private static final int RC_PICK_MEDIA = 101;

    private MaterialCardView cvSelectMedia;
    private View layoutUploadPrompt;
    private ImageView ivMediaPreview, ivVideoPlayIndicator;
    private EditText etCaption;
    private MaterialButton btnUploadPost;

    private Uri selectedMediaUri = null;
    private String selectedMediaType = null; // "image" or "video"

    private String teacherName = "Teacher";
    private String teacherProfile = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_post);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        MaterialToolbar toolbar = findViewById(R.id.toolbarAddPost);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        cvSelectMedia = findViewById(R.id.cvSelectMedia);
        layoutUploadPrompt = findViewById(R.id.layoutUploadPrompt);
        ivMediaPreview = findViewById(R.id.ivMediaPreview);
        ivVideoPlayIndicator = findViewById(R.id.ivVideoPlayIndicator);
        etCaption = findViewById(R.id.etCaption);
        btnUploadPost = findViewById(R.id.btnUploadPost);

        cvSelectMedia.setOnClickListener(v -> pickMedia());
        btnUploadPost.setOnClickListener(v -> uploadPost());

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseDatabase.getInstance().getReference("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            teacherName = snapshot.child("name").getValue(String.class);
                            teacherProfile = snapshot.child("profileImage").getValue(String.class);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                    }
                });
    }

    private void pickMedia() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        startActivityForResult(intent, RC_PICK_MEDIA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_PICK_MEDIA && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                String mimeType = getContentResolver().getType(uri);
                if (mimeType != null && mimeType.startsWith("video")) {
                    if (isVideoTooLong(uri)) {
                        Toast.makeText(this, "Video duration exceeds 10 minutes", Toast.LENGTH_LONG).show();
                        return;
                    }
                    selectedMediaType = "video";
                    ivVideoPlayIndicator.setVisibility(View.VISIBLE);
                } else {
                    selectedMediaType = "image";
                    ivVideoPlayIndicator.setVisibility(View.GONE);
                }

                selectedMediaUri = uri;
                layoutUploadPrompt.setVisibility(View.GONE);
                ivMediaPreview.setVisibility(View.VISIBLE);
                Glide.with(this).load(uri).into(ivMediaPreview);
            }
        }
    }

    private boolean isVideoTooLong(Uri uri) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, uri);
            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (time != null) {
                long durationMs = Long.parseLong(time);
                if (durationMs > 600000) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void uploadPost() {
        if (selectedMediaUri == null) {
            Toast.makeText(this, "Please select an image or video first", Toast.LENGTH_SHORT).show();
            return;
        }

        String caption = etCaption.getText().toString().trim();
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Uploading Post");
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        CloudinaryUploader.upload(this, selectedMediaUri, "posts", new CloudinaryUploader.Callback() {
            @Override
            public void onProgress(int percent) {
                runOnUiThread(() -> progressDialog.setMessage("Uploading... " + percent + "%"));
            }

            @Override
            public void onSuccess(String secureUrl) {
                savePostToFirebase(secureUrl, caption, progressDialog);
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(AddPostActivity.this, "Upload failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void savePostToFirebase(String mediaUrl, String caption, ProgressDialog progressDialog) {
        String teacherUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        long timestamp = System.currentTimeMillis();

        PostModel post = new PostModel(teacherUid, teacherName, teacherProfile, mediaUrl, selectedMediaType, caption, timestamp);

        FirebaseDatabase.getInstance().getReference("posts").push().setValue(post)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Post uploaded successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to save post data", Toast.LENGTH_SHORT).show();
                });
    }
}
