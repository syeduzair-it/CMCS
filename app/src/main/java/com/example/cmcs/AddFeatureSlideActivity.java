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
import com.example.cmcs.models.FeatureSlideModel;
import com.example.cmcs.utils.CloudinaryUploader;
import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AddFeatureSlideActivity extends AppCompatActivity {

    private ImageView ivPreview;
    private ProgressBar loading;
    private MaterialButton btnUpload;
    private TextInputEditText etTitle, etDescription;

    private Uri selectedUri;
    private String teacherUid;
    private String featureId;

    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedUri = uri;
                    ivPreview.setVisibility(View.VISIBLE);
                    Glide.with(this).load(uri).centerCrop().into(ivPreview);
                    updateUploadButton();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_feature);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        featureId = getIntent().getStringExtra("featureId");
        if (featureId == null) {
            finish();
            return;
        }

        ivPreview = findViewById(R.id.af_ivPreview);
        loading = findViewById(R.id.af_loading);
        btnUpload = findViewById(R.id.af_btnUpload);
        etTitle = findViewById(R.id.af_etTitle);
        etDescription = findViewById(R.id.af_etDescription);

        MaterialToolbar toolbar = findViewById(R.id.af_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("Add Slide");
        toolbar.setNavigationOnClickListener(v -> finish());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        teacherUid = user.getUid();

        etTitle.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                updateUploadButton();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        findViewById(R.id.af_btnPickImage).setOnClickListener(v -> imagePicker.launch("image/*"));
        btnUpload.setOnClickListener(v -> checkCountAndUpload());
    }

    private void updateUploadButton() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        btnUpload.setEnabled(!title.isEmpty() && selectedUri != null);
    }

    private void checkCountAndUpload() {
        setLoading(true);
        FirebaseDatabase.getInstance()
                .getReference("features")
                .child(featureId)
                .child("slides")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.getChildrenCount() >= 8) {
                            setLoading(false);
                            Toast.makeText(AddFeatureSlideActivity.this, "Maximum 8 slides allowed.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        uploadImage();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        setLoading(false);
                    }
                });
    }

    private void uploadImage() {
        CloudinaryUploader.upload(this, selectedUri, "features", new CloudinaryUploader.Callback() {
            @Override
            public void onProgress(int percent) {
            }

            @Override
            public void onSuccess(String secureUrl) {
                saveToDb(secureUrl);
            }

            @Override
            public void onFailure(String error) {
                onError(error);
            }
        });
    }

    private void saveToDb(String imageUrl) {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String desc = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";

        FeatureSlideModel model = new FeatureSlideModel(
                imageUrl, title, desc, System.currentTimeMillis(), teacherUid);

        String newSlideKey = FirebaseDatabase.getInstance().getReference("features").child(featureId).child("slides").push().getKey();

        if (newSlideKey != null) {
            FirebaseDatabase.getInstance().getReference("features").child(featureId).child("slides").child(newSlideKey)
                    .setValue(model)
                    .addOnSuccessListener(u -> {
                        setLoading(false);
                        Toast.makeText(this, "Slide added!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> onError(e.getMessage()));
        } else {
            onError("Failed to generate slide key.");
        }
    }

    private void onError(String msg) {
        setLoading(false);
        Toast.makeText(this, "Upload failed. Please try again.", Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean on) {
        loading.setVisibility(on ? View.VISIBLE : View.GONE);
        btnUpload.setEnabled(!on);
        findViewById(R.id.af_btnPickImage).setEnabled(!on);
    }
}
