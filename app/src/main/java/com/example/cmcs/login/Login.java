package com.example.cmcs.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
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

public class Login extends AppCompatActivity {

    EditText inputEmail, inputPassword;
    Switch forgotSwitch;
    Button submitButton;

    FirebaseAuth mAuth;
    DatabaseReference dbRef;

    // Role passed from WelcomeActivity ("student" or "teacher")
    private String loginRole;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        inputEmail = findViewById(R.id.inputPhone); // reusing same view id
        inputPassword = findViewById(R.id.inputPassword);
        forgotSwitch = findViewById(R.id.forgotSwitch);
        submitButton = findViewById(R.id.submitButton);

        mAuth = FirebaseAuth.getInstance();
        dbRef = FirebaseDatabase.getInstance().getReference();

        // Read role sent by WelcomeActivity; default to "student" for safety
        loginRole = getIntent().getStringExtra("loginRole");
        if (loginRole == null) {
            loginRole = "student";
        }

        submitButton.setOnClickListener(v -> handleLogin());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────
    private void handleLogin() {
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();
        boolean isFirstOrForgot = forgotSwitch.isChecked();

        if (TextUtils.isEmpty(email)) {
            inputEmail.setError("Enter Email");
            return;
        }

        if (!isFirstOrForgot && TextUtils.isEmpty(password)) {
            inputPassword.setError("Enter Password");
            return;
        }

        if (isFirstOrForgot) {
            handleFirstTimeOrForgot(email);
        } else {
            normalLogin(email, password);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Normal Login  (with role-spoofing guard)
    // ─────────────────────────────────────────────────────────────────────────
    private void normalLogin(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) {
                        Toast.makeText(Login.this,
                                "Login Failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        return;
                    }
                    String uid = user.getUid();

                    // Role-spoofing guard: verify the UID exists in the expected node
                    if ("teacher".equals(loginRole)) {
                        verifyAndFetchTeacher(uid);
                    } else {
                        verifyAndFetchStudent(uid);
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Role verification helpers (prevent role spoofing)
    // ─────────────────────────────────────────────────────────────────────────
    private void verifyAndFetchStudent(String uid) {
        dbRef.child("students")
                .orderByChild("authUid")
                .equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            mAuth.signOut();
                            Toast.makeText(Login.this,
                                    "Not authorized as student. Contact Admin.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        fetchStudentData(uid);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(Login.this,
                                "Database Error: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void verifyAndFetchTeacher(String uid) {
        dbRef.child("teachers")
                .orderByChild("authUid")
                .equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            mAuth.signOut();
                            Toast.makeText(Login.this,
                                    "Not authorized as teacher. Contact Admin.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        fetchTeacherData(uid);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(Login.this,
                                "Database Error: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // First-time / Forgot Password  (branched by loginRole)
    // ─────────────────────────────────────────────────────────────────────────
    private void handleFirstTimeOrForgot(String email) {
        String node = "teacher".equals(loginRole) ? "teachers" : "students";

        dbRef.child(node)
                .orderByChild("email")
                .equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        if (!snapshot.exists()) {
                            Toast.makeText(Login.this,
                                    "You are not authorized. Contact Admin.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        for (DataSnapshot userSnap : snapshot.getChildren()) {

                            Boolean accountCreated
                                    = userSnap.child("accountCreated").getValue(Boolean.class);

                            if (accountCreated == null || !accountCreated) {

                                // First time → Set Password
                                Intent intent = new Intent(Login.this, SetPassword.class);
                                intent.putExtra("email", email);

                                if ("teacher".equals(loginRole)) {
                                    intent.putExtra("userType", "teacher");
                                    intent.putExtra("teacherKey", userSnap.getKey());
                                } else {
                                    intent.putExtra("userType", "student");
                                    intent.putExtra("studentKey", userSnap.getKey());
                                }

                                startActivity(intent);

                            } else {

                                // Forgot password → send reset email
                                mAuth.sendPasswordResetEmail(email)
                                        .addOnCompleteListener(task -> {
                                            if (task.isSuccessful()) {
                                                Toast.makeText(Login.this,
                                                        "Password reset email sent.",
                                                        Toast.LENGTH_LONG).show();
                                            } else {
                                                Toast.makeText(Login.this,
                                                        "Error: " + task.getException().getMessage(),
                                                        Toast.LENGTH_LONG).show();
                                            }
                                        });
                            }

                            break; // only process first match
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(Login.this,
                                "Database Error: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fetch Student Data
    // ─────────────────────────────────────────────────────────────────────────
    private void fetchStudentData(String authUid) {
        dbRef.child("students")
                .orderByChild("authUid")
                .equalTo(authUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        if (!snapshot.exists()) {
                            Toast.makeText(Login.this,
                                    "Student record not found.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        for (DataSnapshot studentSnap : snapshot.getChildren()) {

                            String name = studentSnap.child("name").getValue(String.class);
                            String phone = studentSnap.child("phone").getValue(String.class);
                            String studentId = studentSnap.child("studentId").getValue(String.class);
                            String department = studentSnap.child("department").getValue(String.class);
                            String course = studentSnap.child("course").getValue(String.class);
                            String year = studentSnap.child("year").getValue(String.class);
                            String gender = studentSnap.child("gender").getValue(String.class);

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

                            // Sync users/ node for chat system
                            syncUserNode(authUid, name, "student", department, gender);

                            Intent intent = new Intent(Login.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            break;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(Login.this,
                                "Database Error: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fetch Teacher Data
    // ─────────────────────────────────────────────────────────────────────────
    private void fetchTeacherData(String authUid) {
        dbRef.child("teachers")
                .orderByChild("authUid")
                .equalTo(authUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        if (!snapshot.exists()) {
                            Toast.makeText(Login.this,
                                    "Teacher record not found.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        for (DataSnapshot teacherSnap : snapshot.getChildren()) {

                            String name = teacherSnap.child("name").getValue(String.class);
                            String phone = teacherSnap.child("phone").getValue(String.class);
                            String department = teacherSnap.child("department").getValue(String.class);
                            String gender = teacherSnap.child("gender").getValue(String.class);
                            String email = teacherSnap.child("email").getValue(String.class);

                            SharedPreferences prefs
                                    = getSharedPreferences("cmcs_prefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("authUid", authUid);
                            editor.putString("role", "teacher");
                            editor.putString("name", name);
                            editor.putString("phone", phone);
                            editor.putString("department", department);
                            editor.putString("gender", gender);
                            editor.putString("email", email);
                            editor.putBoolean("isLoggedIn", true);
                            editor.apply();

                            // Sync users/ node for chat system
                            syncUserNode(authUid, name, "teacher", department, gender);

                            Intent intent = new Intent(Login.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            break;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(Login.this,
                                "Database Error: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
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
        updates.put("profileImage", "");   // preserve any existing image via updateChildren

        userRef.updateChildren(updates);  // updateChildren won't wipe fields not listed
    }
}
