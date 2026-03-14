package com.example.cmcs;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.adapters.ClassSelectionAdapter;
import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClassSelectionActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView recyclerClasses;
    private ProgressBar progressBar;
    private ClassSelectionAdapter adapter;
    private List<String> classList;
    private String userDepartment = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_selection);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        toolbar = findViewById(R.id.toolbarClassSelection);
        recyclerClasses = findViewById(R.id.recyclerClasses);
        progressBar = findViewById(R.id.progressBarClasses);

        toolbar.setNavigationOnClickListener(v -> finish());

        classList = new ArrayList<>();
        recyclerClasses.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ClassSelectionAdapter(this, classList, className -> {
            Intent intent = new Intent(ClassSelectionActivity.this, AttendanceSessionActivity.class);
            intent.putExtra("classId", className);
            intent.putExtra("department", userDepartment);
            startActivity(intent);
        });
        recyclerClasses.setAdapter(adapter);

        fetchUserDepartmentAndPopulateClasses();
    }

    private void fetchUserDepartmentAndPopulateClasses() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String uid = currentUser.getUid();
        progressBar.setVisibility(View.VISIBLE);
        recyclerClasses.setVisibility(View.GONE);

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);
                recyclerClasses.setVisibility(View.VISIBLE);

                if (snapshot.exists() && snapshot.hasChild("department")) {
                    userDepartment = snapshot.child("department").getValue(String.class);
                    if (userDepartment == null) {
                        userDepartment = "";
                    }

                    String deptLower = userDepartment.toLowerCase().trim();
                    classList.clear();

                    if (deptLower.equals("computer")) {
                        classList.addAll(Arrays.asList("BCA 1", "BCA 2", "BCA 3", "BSC 1", "BSC 2", "BSC 3", "MSC 1", "MSC 2"));
                    } else if (deptLower.equals("management")) {
                        classList.addAll(Arrays.asList("BBA 1", "BBA 2", "BBA 3", "BCOM 1", "BCOM 2", "BCOM 3", "MBA 1", "MBA 2"));
                    } else {
                        Toast.makeText(ClassSelectionActivity.this, "Unknown department configured: " + userDepartment, Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(ClassSelectionActivity.this, "Department not configured.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ClassSelectionActivity.this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}
