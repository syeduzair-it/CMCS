package com.example.cmcs;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.adapters.ClassAdapter;
import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * NotesClassActivity
 *
 * Teacher: loads their department, then lists every unique (course, year) node
 * that exists under notes/<dept>.
 *
 * Student: reads their own course + year from Firebase and jumps directly to
 * NotesSubjectActivity — no class picker needed.
 */
public class NotesClassActivity extends AppCompatActivity {

    public static final String EXTRA_DEPT = "dept";
    public static final String EXTRA_COURSE = "course";
    public static final String EXTRA_YEAR = "year";
    public static final String EXTRA_ROLE = "role";

    private ProgressBar loading;
    private TextView tvEmpty;
    private RecyclerView rv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes_class);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        loading = findViewById(R.id.nc_loading);
        tvEmpty = findViewById(R.id.nc_empty);
        rv = findViewById(R.id.rv_classes);
        rv.setLayoutManager(new LinearLayoutManager(this));

        MaterialToolbar toolbar = findViewById(R.id.nc_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        loadUserThenSetup(user.getUid());
    }

    private void loadUserThenSetup(String uid) {
        FirebaseDatabase.getInstance().getReference("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String role = snapshot.child("role").getValue(String.class);
                        String dept = snapshot.child("department").getValue(String.class);
                        if (dept == null) {
                            dept = "";
                        }

                        if ("teacher".equals(role)) {
                            loadClassesForTeacher(dept, uid);
                        } else {
                            // Student → jump straight to subject list for their class
                            String course = snapshot.child("course").getValue(String.class);
                            String year = snapshot.child("year").getValue(String.class);
                            if (course == null) {
                                course = "";
                            }
                            if (year == null) {
                                year = "";
                            }
                            openSubjects(dept, course, year, "student", uid);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        loading.setVisibility(View.GONE);
                        Toast.makeText(NotesClassActivity.this,
                                "Failed to load user", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadClassesForTeacher(String dept, String uid) {
        // Query users/ filtered by department — works even when no notes exist yet.
        FirebaseDatabase.getInstance()
                .getReference("users")
                .orderByChild("department")
                .equalTo(dept)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        loading.setVisibility(View.GONE);

                        // Collect unique course|year pairs from student records.
                        Set<String> set = new LinkedHashSet<>();
                        for (DataSnapshot userSnap : snapshot.getChildren()) {
                            String role = userSnap.child("role").getValue(String.class);
                            if (!"student".equals(role)) {
                                continue; // skip teachers or admins
                            }
                            String course = userSnap.child("course").getValue(String.class);
                            String year = userSnap.child("year").getValue(String.class);
                            if (course != null && !course.isEmpty()
                                    && year != null && !year.isEmpty()) {
                                set.add(course + "|" + year);
                            }
                        }

                        List<String> items = new ArrayList<>(set);
                        if (items.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rv.setVisibility(View.GONE);
                        } else {
                            tvEmpty.setVisibility(View.GONE);
                            rv.setVisibility(View.VISIBLE);
                            // course/year come directly from users/ — no sanitization needed.
                            rv.setAdapter(new ClassAdapter(items,
                                    (course, year) -> openSubjects(dept, course, year, "teacher", uid)));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        loading.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void openSubjects(String dept, String course, String year,
            String role, String uid) {
        Intent i = new Intent(this, NotesSubjectActivity.class);
        i.putExtra(EXTRA_DEPT, dept);
        i.putExtra(EXTRA_COURSE, course);
        i.putExtra(EXTRA_YEAR, year);
        i.putExtra(EXTRA_ROLE, role);
        i.putExtra("uid", uid);
        startActivity(i);
        // If student, close this intermediate screen
        if (!"teacher".equals(role)) {
            finish();
        }
    }

    static String sanitize(String s) {
        if (s == null) {
            return "_";
        }
        return s.trim().replace(" ", "_").replace(".", "_")
                .replace("#", "_").replace("$", "_")
                .replace("[", "_").replace("]", "_");
    }

    static String unsanitize(String s) {
        return s == null ? "" : s.replace("_", " ");
    }
}
