package com.example.cmcs;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.cmcs.models.FeatureModel;
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

/**
 * AddFeatureActivity — Teachers only.
 *
 * Allows a teacher to add a new feature slide (max 5). Picks an image, uploads
 * it to Cloudinary (folder: features), writes FeatureModel to:
 * features/<nextKey>
 *
 * Key selection: scans feature1…feature5 and picks the first empty slot.
 */
public class AddFeatureActivity extends AppCompatActivity {

    private static final int MAX_FEATURES = 5;

    private ImageView ivPreview;
    private ProgressBar loading;
    private MaterialButton btnUpload;
    private TextInputEditText etTitle, etDescription;

    private Uri selectedUri;
    private String teacherUid;
    private String teacherName;

    private final ActivityResultLauncher<String> imagePicker
            = registerForActivityResult(
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

        FirebaseDatabase.getInstance().getReference("features").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.getChildrenCount() >= MAX_FEATURES) {
                    Toast.makeText(AddFeatureActivity.this, "Maximum 5 features allowed.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

        ivPreview = findViewById(R.id.af_ivPreview);
        loading = findViewById(R.id.af_loading);
        btnUpload = findViewById(R.id.af_btnUpload);
        etTitle = findViewById(R.id.af_etTitle);
        etDescription = findViewById(R.id.af_etDescription);

        MaterialToolbar toolbar = findViewById(R.id.af_toolbar);
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

    // ── Upload flow ───────────────────────────────────────────────────────
    private void checkCountAndUpload() {
        setLoading(true);
        FirebaseDatabase.getInstance().getReference("features")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.getChildrenCount() >= MAX_FEATURES) {
                            setLoading(false);
                            Toast.makeText(AddFeatureActivity.this,
                                    "Maximum 5 features allowed.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String nextKey = FirebaseDatabase.getInstance().getReference("features").push().getKey();
                        if (nextKey == null) {
                            setLoading(false);
                            Toast.makeText(AddFeatureActivity.this,
                                    "No slot available.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        uploadImage(nextKey);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        setLoading(false);
                    }
                });
    }

    private void uploadImage(String featureKey) {
        CloudinaryUploader.upload(this, selectedUri, "features", new CloudinaryUploader.Callback() {
            @Override
            public void onProgress(int percent) {
                // loading spinner already visible; no-op
            }

            @Override
            public void onSuccess(String secureUrl) {
                saveToDb(featureKey, secureUrl);
            }

            @Override
            public void onFailure(String error) {
                onError(error);
            }
        });
    }

    private void saveToDb(String featureKey, String imageUrl) {
        String title = etTitle.getText() != null
                ? etTitle.getText().toString().trim() : "";
        String desc = etDescription.getText() != null
                ? etDescription.getText().toString().trim() : "";

        long time = System.currentTimeMillis();
        FeatureModel model = new FeatureModel(title, teacherName, time);

        com.example.cmcs.models.FeatureSlideModel slide = new com.example.cmcs.models.FeatureSlideModel(
                imageUrl, title, desc, time, teacherName);
        String slideKey = FirebaseDatabase.getInstance().getReference("features").child(featureKey).child("slides").push().getKey();
        if (slideKey != null) {
            model.getSlides().put(slideKey, slide);
        }

        FirebaseDatabase.getInstance().getReference("features").child(featureKey)
                .setValue(model)
                .addOnSuccessListener(u -> {
                    setLoading(false);
                    Toast.makeText(this, "Feature added!", Toast.LENGTH_SHORT).show();
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
        btnUpload.setEnabled(!on);
        findViewById(R.id.af_btnPickImage).setEnabled(!on);
    }
}
