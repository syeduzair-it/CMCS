package com.example.cmcs;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * UniversityCredentialsActivity — Phase 4
 *
 * Reads universityLoginId and universityPassword from users/<uid>. Password is
 * always masked on load.
 *
 * Flow: 1. "View / Edit" button → dialog: enter CMCS account password 2.
 * Reauthenticate via Firebase → on success: unmask, enable editing 3. Button
 * becomes "Save" → writes to Firebase → masks again, disables fields
 *
 * Security: • No reauthentication → no editing, ever. • Null email guard
 * prevents crash for malformed accounts.
 */
public class UniversityCredentialsActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────
    private ProgressBar loadingSpinner;
    private CardView cardCredentials;
    private TextInputEditText etLoginId;
    private TextInputEditText etPassword;
    private MaterialButton btnViewEdit;

    // ── Firebase ──────────────────────────────────────────────────────────
    private FirebaseUser firebaseUser;
    private DatabaseReference userRef;

    // ── State ─────────────────────────────────────────────────────────────
    /**
     * True after successful reauthentication — edit mode is active.
     */
    private boolean editUnlocked = false;

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_university_credentials);

        bindViews();
        setupToolbar();

        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            showError("Not signed in.");
            return;
        }

        userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(firebaseUser.getUid());

        loadCredentials();

        btnViewEdit.setOnClickListener(v -> {
            if (!editUnlocked) {
                showReauthDialog();
            } else {
                saveCredentials();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // View binding
    // ─────────────────────────────────────────────────────────────────────
    private void bindViews() {
        loadingSpinner = findViewById(R.id.uni_loading);
        cardCredentials = findViewById(R.id.card_credentials);
        etLoginId = findViewById(R.id.et_login_id);
        etPassword = findViewById(R.id.et_password);
        btnViewEdit = findViewById(R.id.btn_view_edit);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Toolbar
    // ─────────────────────────────────────────────────────────────────────
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.uni_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Firebase — load credentials
    // ─────────────────────────────────────────────────────────────────────
    private void loadCredentials() {
        loadingSpinner.setVisibility(View.VISIBLE);
        cardCredentials.setVisibility(View.GONE);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                loadingSpinner.setVisibility(View.GONE);
                cardCredentials.setVisibility(View.VISIBLE);

                // Null-safe: if fields don't exist yet, show empty
                String loginId = snapshot.child("universityLoginId").getValue(String.class);
                String password = snapshot.child("universityPassword").getValue(String.class);

                etLoginId.setText(loginId != null ? loginId : "");
                etPassword.setText(password != null ? password : "");

                // Always start masked & disabled
                setEditMode(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loadingSpinner.setVisibility(View.GONE);
                Toast.makeText(UniversityCredentialsActivity.this,
                        "Failed to load credentials", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Reauthentication dialog
    // ─────────────────────────────────────────────────────────────────────
    private void showReauthDialog() {
        // Guard: need email for EmailAuthProvider
        String email = firebaseUser.getEmail();
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this,
                    "Cannot reauthenticate: account has no email",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Build dialog with a password input
        EditText inputPassword = new EditText(this);
        inputPassword.setHint("CMCS account password");
        inputPassword.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int padding = dpToPx(20);
        inputPassword.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle("Verify Identity")
                .setMessage("Enter your CMCS account password to continue.")
                .setView(inputPassword)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String entered = inputPassword.getText().toString().trim();
                    if (entered.isEmpty()) {
                        Toast.makeText(this, "Password cannot be empty",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    reauthenticate(email, entered);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Firebase reauthentication
    // ─────────────────────────────────────────────────────────────────────
    private void reauthenticate(String email, String password) {
        loadingSpinner.setVisibility(View.VISIBLE);
        btnViewEdit.setEnabled(false);

        AuthCredential credential = EmailAuthProvider.getCredential(email, password);
        firebaseUser.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    btnViewEdit.setEnabled(true);

                    if (task.isSuccessful()) {
                        // ✓ Reauthentication passed — unlock editing
                        editUnlocked = true;
                        setEditMode(true);
                        Toast.makeText(this,
                                "Identity verified. You may now edit.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                "Authentication failed. Wrong password.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Firebase — save credentials
    // ─────────────────────────────────────────────────────────────────────
    private void saveCredentials() {
        String loginId = etLoginId.getText() != null
                ? etLoginId.getText().toString().trim() : "";
        String password = etPassword.getText() != null
                ? etPassword.getText().toString().trim() : "";

        loadingSpinner.setVisibility(View.VISIBLE);
        btnViewEdit.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("universityLoginId", loginId);
        updates.put("universityPassword", password);

        userRef.updateChildren(updates)
                .addOnCompleteListener(task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    btnViewEdit.setEnabled(true);

                    if (task.isSuccessful()) {
                        // Reset to locked/masked view
                        editUnlocked = false;
                        setEditMode(false);
                        Toast.makeText(this,
                                "Saved successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                "Save failed. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────
    // UI state helpers
    // ─────────────────────────────────────────────────────────────────────
    /**
     * editMode=false → fields disabled, password masked, button = "View / Edit"
     * editMode=true → fields enabled, password visible as text, button = "Save"
     */
    private void setEditMode(boolean editMode) {
        etLoginId.setEnabled(editMode);
        etPassword.setEnabled(editMode);

        if (editMode) {
            // Show plaintext when editing so user can see what they're typing
            etPassword.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            btnViewEdit.setText("Save");
        } else {
            // Always mask when not in edit mode
            etPassword.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            // Keep cursor at end so masking looks clean
            if (etPassword.getText() != null) {
                etPassword.setSelection(etPassword.getText().length());
            }
            btnViewEdit.setText("View / Edit");
        }
    }

    private void showError(String msg) {
        loadingSpinner.setVisibility(View.GONE);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
