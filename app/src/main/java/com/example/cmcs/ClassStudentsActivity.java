package com.example.cmcs;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.adapters.UserSelectionAdapter;
import com.example.cmcs.models.UserModel;
import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * ClassStudentsActivity — shows all students in a specific class (dept + course
 * + year).
 *
 * Data strategy: Always loads fresh from Firebase students/ node so no student
 * is ever missed due to a stale or incomplete cache. A student is included only
 * when accountCreated == true AND authUid is present.
 */
public class ClassStudentsActivity extends AppCompatActivity {

    private String targetDepartment;
    private String targetCourse;
    private String targetYear;

    private RecyclerView studentsRecyclerView;
    private LinearLayout emptyState;
    private TextInputEditText searchInput;
    private UserSelectionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_students);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        // Retrieve intent extras
        targetDepartment = getIntent().getStringExtra("department");
        targetCourse = getIntent().getStringExtra("course");
        targetYear = getIntent().getStringExtra("year");

        if (targetDepartment == null || targetCourse == null || targetYear == null) {
            finish();
            return;
        }

        bindViews();

        MaterialToolbar toolbar = findViewById(R.id.toolbar_class_students);
        toolbar.setTitle(targetCourse + " " + targetYear + " Students");
        toolbar.setNavigationOnClickListener(v -> finish());

        setupRecyclerView();
        setupSearch();

        // Always reload from Firebase to guarantee no student is missed.
        Log.d("StudentLoader", "Starting Firebase load for "
                + targetCourse + " year=" + targetYear + " dept=" + targetDepartment);
        loadStudentsFromFirebase();
    }

    // ─────────────────────────────────────────────────────────────────────
    // View binding
    // ─────────────────────────────────────────────────────────────────────
    private void bindViews() {
        studentsRecyclerView = findViewById(R.id.students_recycler_view);
        emptyState = findViewById(R.id.empty_state);
        searchInput = findViewById(R.id.search_input);
    }

    private void setupRecyclerView() {
        adapter = new UserSelectionAdapter(this, new ArrayList<>());
        studentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        studentsRecyclerView.setAdapter(adapter);
        adapter.setOnUserClickListener(this::onUserSelected);
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Path A: filter from already-populated cache
    // ─────────────────────────────────────────────────────────────────────
    private void filterAndDisplay(List<UserModel> allUsers) {
        List<UserModel> filteredStudents = new ArrayList<>();

        for (UserModel u : allUsers) {
            String name = u.getName() != null ? u.getName() : "(unnamed)";

            // Must have a valid UID (guaranteed by Firebase loader; guards against cache entries)
            if (u.getAuthUid() == null) {
                Log.d("StudentLoader", "Skipping student: " + name + " (no authUid)");
                continue;
            }

            boolean deptMatch = normalize(targetDepartment).equals(normalize(u.getDepartment()));
            boolean courseMatch = normalize(targetCourse).equals(normalize(u.getCourse()));
            boolean yearMatch = normalize(u.getYear()).contains(normalize(targetYear));

            if (deptMatch && courseMatch && yearMatch) {
                Log.d("StudentLoader", "Loaded student: " + name);
                filteredStudents.add(u);
            } else {
                Log.d("StudentLoader", "Skipping student: " + name
                        + " (dept=" + u.getDepartment()
                        + ", course=" + u.getCourse()
                        + ", year=" + u.getYear() + ")");
            }
        }

        filteredStudents.sort((a, b) -> {
            String an = a.getName() != null ? a.getName() : "";
            String bn = b.getName() != null ? b.getName() : "";
            return an.compareToIgnoreCase(bn);
        });

        Log.d("StudentLoader", "Final count: " + filteredStudents.size()
                + " for dept=" + targetDepartment
                + ", course=" + targetCourse
                + ", year=" + targetYear);

        updateUI(filteredStudents);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Path B: cache was empty — reload students/ from Firebase directly
    // ─────────────────────────────────────────────────────────────────────
    private void loadStudentsFromFirebase() {
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("students");

        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<UserModel> tempUsers = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String name = child.child("name").getValue(String.class);
                    if (name == null) {
                        name = "(unnamed)";
                    }

                    // Only include students whose account has been activated
                    Boolean accountCreated = child.child("accountCreated").getValue(Boolean.class);
                    String authUid = child.child("authUid").getValue(String.class);

                    if (!Boolean.TRUE.equals(accountCreated) || authUid == null) {
                        Log.d("StudentLoader", "Skipping student: " + name
                                + " (accountCreated=" + accountCreated
                                + ", authUid=" + authUid + ")");
                        continue;
                    }

                    UserModel u = new UserModel();
                    u.setAuthUid(authUid);
                    u.setName(name);
                    u.setDepartment(child.child("department").getValue(String.class));
                    u.setCourse(child.child("course").getValue(String.class));
                    u.setYear(child.child("year").getValue(String.class));
                    u.setGender(child.child("gender").getValue(String.class));
                    u.setRole("student");
                    u.setProfileImage(child.child("profileImage").getValue(String.class));
                    tempUsers.add(u);
                }

                Log.d("StudentLoader", "Firebase raw total: " + snapshot.getChildrenCount()
                        + ", activated: " + tempUsers.size());
                filterAndDisplay(tempUsers);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ClassStudentsActivity.this,
                        "Failed to load students: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // UI update
    // ─────────────────────────────────────────────────────────────────────
    private void updateUI(List<UserModel> filteredStudents) {
        if (filteredStudents.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            studentsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            studentsRecyclerView.setVisibility(View.VISIBLE);
            adapter.updateData(filteredStudents);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────
    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private void onUserSelected(UserModel other) {
        String otherUid = other.getAuthUid();
        String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (otherUid == null || myUid == null) {
            return;
        }

        // Deterministic chatId: sort UIDs lexicographically
        String chatId = myUid.compareTo(otherUid) < 0
                ? myUid + "_" + otherUid
                : otherUid + "_" + myUid;

        openChat(chatId, other);
    }

    private void openChat(String chatId, UserModel other) {
        startActivity(ChatActivity.newIntent(
                this,
                chatId,
                other.getAuthUid(),
                other.getName(),
                other.getProfileImage()
        ));
        finish();
    }
}
