package com.example.cmcs;

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
import com.example.cmcs.models.HighlightMediaModel;
import com.example.cmcs.models.HighlightModel;
import com.example.cmcs.utils.CloudinaryUploader;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * AddHighlightActivity — Teachers only.
 *
 * Creates a new highlight album and uploads the first media item via Cloudinary
 * (folder: highlights). DB structure written: highlights/<highlightId> —
 * HighlightModel highlights/<highlightId>/media/<mediaId> — HighlightMediaModel
 */
public class AddHighlightActivity extends AppCompatActivity {

    private ImageView ivPreview;
    private ProgressBar loading;
    private MaterialButton btnCreate;
    private TextInputEditText etTitle;

    private Uri selectedUri;
    private String mimeType;
    private String teacherUid;
    private String teacherName;

    private final ActivityResultLauncher<String> mediaPicker
            = registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            selectedUri = uri;
                            mimeType = resolveMimeType(uri);
                            ivPreview.setVisibility(View.VISIBLE);
                            Glide.with(this).load(uri).thumbnail(0.3f)
                                    .centerCrop().into(ivPreview);
                            updateCreateButton();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_highlight);

        ivPreview = findViewById(R.id.ah_ivPreview);
        loading = findViewById(R.id.ah_loading);
        btnCreate = findViewById(R.id.ah_btnCreate);
        etTitle = findViewById(R.id.ah_etTitle);

        MaterialToolbar toolbar = findViewById(R.id.ah_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        teacherUid = user.getUid();

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

        etTitle.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                updateCreateButton();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        findViewById(R.id.ah_btnPickMedia).setOnClickListener(v -> mediaPicker.launch("image/* video/*"));
        btnCreate.setOnClickListener(v -> createHighlight());
    }

    private void updateCreateButton() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        btnCreate.setEnabled(!title.isEmpty() && selectedUri != null);
    }

    // ── Create highlight ──────────────────────────────────────────────────
    private void createHighlight() {
        setLoading(true);
        DatabaseReference hlRef = FirebaseDatabase.getInstance()
                .getReference("highlights").push();
        String highlightId = hlRef.getKey();
        if (highlightId == null) {
            setLoading(false);
            return;
        }

        String title = etTitle.getText() != null
                ? etTitle.getText().toString().trim() : "";

        HighlightModel model = new HighlightModel(
                title, teacherUid, teacherName, System.currentTimeMillis());

        // Write the highlight node first, then upload the media
        hlRef.setValue(model)
                .addOnSuccessListener(u1 -> uploadMedia(highlightId))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void uploadMedia(String highlightId) {
        String mediaId = FirebaseDatabase.getInstance()
                .getReference("highlights").push().getKey();
        if (mediaId == null) {
            setLoading(false);
            return;
        }

        CloudinaryUploader.upload(this, selectedUri, "highlights", new CloudinaryUploader.Callback() {
            @Override
            public void onProgress(int percent) {
                // loading spinner already visible; no-op
            }

            @Override
            public void onSuccess(String secureUrl) {
                saveMediaToDb(highlightId, mediaId, secureUrl);
            }

            @Override
            public void onFailure(String error) {
                onError(error);
            }
        });
    }

    private void saveMediaToDb(String highlightId, String mediaId, String downloadUrl) {
        HighlightMediaModel media = new HighlightMediaModel(
                downloadUrl, mimeType, System.currentTimeMillis());
        media.setMediaId(mediaId);

        FirebaseDatabase.getInstance()
                .getReference("highlights").child(highlightId).child("media").child(mediaId)
                .setValue(media)
                .addOnSuccessListener(u -> {
                    setLoading(false);
                    Toast.makeText(this, "Highlight created!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> onError(e.getMessage()));
    }

    private void onError(String msg) {
        setLoading(false);
        Toast.makeText(this, "Upload failed. Please try again.", Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean on) {
        loading.setVisibility(on ? View.VISIBLE : View.GONE);
        btnCreate.setEnabled(!on);
        findViewById(R.id.ah_btnPickMedia).setEnabled(!on);
    }

    private String resolveMimeType(Uri uri) {
        String mime = getContentResolver().getType(uri);
        return (mime != null && mime.startsWith("video")) ? "video" : "image";
    }
}
