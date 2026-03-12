package com.example.cmcs.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cmcs.MainActivity;
import com.example.cmcs.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Map;

public class SetPassword extends AppCompatActivity {

    EditText etPassword, etConfirmPassword;
    Button btnSetPassword;

    // Common
    String userType; // "student" or "teacher"
    String email;

    // Student-specific
    String studentKey;

    // Teacher-specific
    String teacherKey;

    FirebaseAuth mAuth;
    DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_password);

        etPassword = findViewById(R.id.editNewPassword);
        etConfirmPassword = findViewById(R.id.editConfirmPassword);
        btnSetPassword = findViewById(R.id.btnSetPassword);

        mAuth = FirebaseAuth.getInstance();
        dbRef = FirebaseDatabase.getInstance().getReference();

        // Read intent extras
        userType = getIntent().getStringExtra("userType");
        email = getIntent().getStringExtra("email");
        studentKey = getIntent().getStringExtra("studentKey");
        teacherKey = getIntent().getStringExtra("teacherKey");

        // Default to student if not provided (backward compat)
        if (userType == null) {
            userType = "student";
        }

        btnSetPassword.setOnClickListener(v -> createAccount());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Create Firebase Auth account (shared for both roles)
    // ─────────────────────────────────────────────────────────────────────────
    private void createAccount() {
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, "Please fill both fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            handleAccountReady(user.getUid());
                        }
                    } else {
                        Toast.makeText(this,
                                "Error: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Post-creation: update DB + SharedPrefs + users/ node based on role
    // ─────────────────────────────────────────────────────────────────────────
    private void handleAccountReady(String authUid) {
        if ("teacher".equals(userType)) {
            handleTeacherAccountReady(authUid);
        } else {
            handleStudentAccountReady(authUid);
        }
    }

    // ── STUDENT ──────────────────────────────────────────────────────────────
    private void handleStudentAccountReady(String authUid) {
        DatabaseReference studentRef = dbRef.child("students").child(studentKey);
        studentRef.child("accountCreated").setValue(true);
        studentRef.child("authUid").setValue(authUid);

        studentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.child("name").getValue(String.class);
                String phone = snapshot.child("phone").getValue(String.class);
                String studentId = snapshot.child("studentId").getValue(String.class);
                String department = snapshot.child("department").getValue(String.class);
                String course = snapshot.child("course").getValue(String.class);
                String year = snapshot.child("year").getValue(String.class);
                String gender = snapshot.child("gender").getValue(String.class);

                // Create users/ node
                syncUserNode(authUid, name, "student", department, gender);

                // Save SharedPreferences
                SharedPreferences prefs
                        = getSharedPreferences("cmcs_prefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("authUid", authUid);
                editor.putString("role", "student");
                editor.putString("name", name);
                editor.putString("phone", phone);
                editor.putString("studentId", studentId);
                editor.putString("department", department);
                editor.putString("course", course);
                editor.putString("year", year);
                editor.putString("gender", gender);
                editor.putBoolean("isLoggedIn", true);
                editor.apply();

                Toast.makeText(SetPassword.this,
                        "Account Created Successfully!", Toast.LENGTH_LONG).show();

                Intent intent = new Intent(SetPassword.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SetPassword.this,
                        error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── TEACHER ──────────────────────────────────────────────────────────────
    private void handleTeacherAccountReady(String authUid) {
        DatabaseReference teacherRef = dbRef.child("teachers").child(teacherKey);
        teacherRef.child("authUid").setValue(authUid);
        teacherRef.child("accountCreated").setValue(true);

        teacherRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.child("name").getValue(String.class);
                String phone = snapshot.child("phone").getValue(String.class);
                String department = snapshot.child("department").getValue(String.class);
                String gender = snapshot.child("gender").getValue(String.class);
                String emailVal = snapshot.child("email").getValue(String.class);

                // Create users/ node with teacher role
                syncUserNode(authUid, name, "teacher", department, gender);

                // Save SharedPreferences
                SharedPreferences prefs
                        = getSharedPreferences("cmcs_prefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("authUid", authUid);
                editor.putString("role", "teacher");
                editor.putString("name", name);
                editor.putString("phone", phone);
                editor.putString("department", department);
                editor.putString("gender", gender);
                editor.putString("email", emailVal);
                editor.putBoolean("isLoggedIn", true);
                editor.apply();

                Toast.makeText(SetPassword.this,
                        "Teacher Account Created Successfully!", Toast.LENGTH_LONG).show();

                Intent intent = new Intent(SetPassword.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SetPassword.this,
                        error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sync users/<authUid> node — required for chat system & SelectUserActivity
    // ─────────────────────────────────────────────────────────────────────────
    private void syncUserNode(String authUid, String name, String role,
            String department, String gender) {
        DatabaseReference userRef = dbRef.child("users").child(authUid);

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name != null ? name : "");
        updates.put("role", role != null ? role : "");
        updates.put("department", department != null ? department : "");
        updates.put("gender", gender != null ? gender : "");
        updates.put("profileImage", "");

        userRef.updateChildren(updates);
    }
}
