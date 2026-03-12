package com.example.cmcs;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.adapters.StudentAttendanceAdapter;
import com.example.cmcs.models.StudentModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class StudentListAttendanceActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvTotalCount, tvPresentCount, tvAbsentCount, tvQrCount, tvManualCount;
    private SearchView searchViewStudents;
    private RecyclerView recyclerViewStudents;
    private ProgressBar progressBar;

    private StudentAttendanceAdapter adapter;
    private List<StudentAttendanceAdapter.StudentAttendanceState> allStudentStates = new ArrayList<>();
    private List<StudentModel> classStudents = new ArrayList<>();
    private HashMap<String, DataSnapshot> attendanceMap = new HashMap<>();

    private String classId;
    private String currentDate;
    private String parsedCourse = "";
    private String parsedYear = "";

    private ActionMode actionMode;

    private int totalStudents = 0;
    private int presentCount = 0;
    private int absentCount = 0;
    private int qrCount = 0;
    private int manualCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_list_attendance);

        toolbar = findViewById(R.id.toolbarStudentList);
        tvTotalCount = findViewById(R.id.tvTotalCount);
        tvPresentCount = findViewById(R.id.tvPresentCount);
        tvAbsentCount = findViewById(R.id.tvAbsentCount);
        tvQrCount = findViewById(R.id.tvQrCount);
        tvManualCount = findViewById(R.id.tvManualCount);
        searchViewStudents = findViewById(R.id.searchViewStudents);
        recyclerViewStudents = findViewById(R.id.recyclerViewStudents);
        progressBar = findViewById(R.id.progressBarStudentList);

        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerViewStudents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StudentAttendanceAdapter(this, allStudentStates, new StudentAttendanceAdapter.OnSelectionChangedListener() {
            @Override
            public void onSelectionModeStarted() {
                if (actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback);
                }
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
                if (actionMode != null) {
                    if (selectedCount == 0) {
                        actionMode.finish();
                    } else {
                        actionMode.setTitle(selectedCount + " Selected");
                    }
                }
            }
        });
        recyclerViewStudents.setAdapter(adapter);

        searchViewStudents.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return true;
            }
        });

        classId = getIntent().getStringExtra("classId");
        currentDate = getIntent().getStringExtra("date");

        if (classId != null) {
            String[] parts = classId.split(" ");
            if (parts.length >= 2) {
                parsedCourse = parts[0].toLowerCase();
                switch (parts[1]) {

                        case "1":
                            parsedYear = "1st_year";
                            break;
                        case "2":
                            parsedYear = "2nd_year";
                            break;
                        case "3":
                            parsedYear = "3rd_year";
                            break;
                        case "4":
                            parsedYear = "4th_year";
                            break;
                        case "5":
                            parsedYear = "5th_year";
                            break;

                }
            }
            toolbar.setTitle(classId);
        }

        if (currentDate == null) {
            currentDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        }

        loadData();
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);

        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("students");
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                classStudents.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StudentModel student = ds.getValue(StudentModel.class);
                    if (student != null) {
                        if (student.getUid() == null) {
                            student.setUid(ds.getKey());
                        }
                        Log.d("StudentDebug",
                                "Course=" + student.getCourse() +
                                        " Year=" + student.getYear() +
                                        " ParsedCourse=" + parsedCourse +
                                        " ParsedYear=" + parsedYear);
                        if (student.getCourse() != null &&
                                student.getYear() != null &&
                                parsedCourse.equalsIgnoreCase(student.getCourse()) &&
                                parsedYear.equalsIgnoreCase(student.getYear())) {
                            classStudents.add(student);
                        }
                    }
                }

                Collections.sort(classStudents, (s1, s2) -> {
                    try {
                        int r1 = Integer.parseInt(s1.getRollNumber());
                        int r2 = Integer.parseInt(s2.getRollNumber());
                        return Integer.compare(r1, r2);
                    } catch (Exception e) {
                        return 0;
                    }
                });

                android.util.Log.d("StudentListDebug", "Filtered students: " + classStudents.size());

                if (classStudents.size() == 0) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(StudentListAttendanceActivity.this, "No students found for this class.", Toast.LENGTH_SHORT).show();
                    return;
                }

                loadAttendanceData();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(StudentListAttendanceActivity.this, "Failed to load students", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAttendanceData() {
        if (classId == null || currentDate == null) {
            progressBar.setVisibility(View.GONE);
            return;
        }

        DatabaseReference attendanceRef = FirebaseDatabase.getInstance().getReference("attendance").child(classId).child(currentDate);
        attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                attendanceMap.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    attendanceMap.put(ds.getKey(), ds);
                }

                mergeData();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(StudentListAttendanceActivity.this, "Failed to load attendance", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void mergeData() {
        allStudentStates.clear();
        totalStudents = classStudents.size();
        presentCount = 0;
        qrCount = 0;
        manualCount = 0;

        for (StudentModel student : classStudents) {
            String attUid = (student.getAuthUid() != null && !student.getAuthUid().isEmpty()) ? student.getAuthUid() : student.getUid();

            boolean isPresent = false;
            String method = "";

            if (attendanceMap.containsKey(attUid)) {
                isPresent = true;
                DataSnapshot ds = attendanceMap.get(attUid);
                method = ds.child("method").getValue(String.class);

                presentCount++;
                if ("qr".equals(method)) {
                    qrCount++;
                } else if ("manual".equals(method)) {
                    manualCount++;
                }
            }

            allStudentStates.add(new StudentAttendanceAdapter.StudentAttendanceState(student, isPresent, method));
        }

        absentCount = totalStudents - presentCount;

        updateSummaryBar();

        adapter.updateData(allStudentStates);
        progressBar.setVisibility(View.GONE);
    }

    private void updateSummaryBar() {
        tvTotalCount.setText(String.valueOf(totalStudents));
        tvPresentCount.setText(String.valueOf(presentCount));
        tvAbsentCount.setText(String.valueOf(absentCount));
        tvQrCount.setText(String.valueOf(qrCount));
        tvManualCount.setText(String.valueOf(manualCount));
    }

    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_attendance_batch_actions, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            List<StudentAttendanceAdapter.StudentAttendanceState> selected = adapter.getSelectedStudents();
            if (selected.isEmpty()) {
                mode.finish();
                return true;
            }

            if (item.getItemId() == R.id.action_mark_present) {
                batchMarkPresent(selected);
                mode.finish();
                return true;
            } else if (item.getItemId() == R.id.action_mark_absent) {
                confirmBatchMarkAbsent(selected, mode);
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            adapter.exitSelectionMode();
        }
    };

    private void batchMarkPresent(List<StudentAttendanceAdapter.StudentAttendanceState> selected) {
        DatabaseReference attendanceRef = FirebaseDatabase.getInstance().getReference("attendance").child(classId).child(currentDate);

        for (StudentAttendanceAdapter.StudentAttendanceState state : selected) {
            if (!state.isPresent) {
                String attUid = (state.student.getAuthUid() != null && !state.student.getAuthUid().isEmpty()) ? state.student.getAuthUid() : state.student.getUid();

                HashMap<String, Object> data = new HashMap<>();
                data.put("name", state.student.getName());
                data.put("timestamp", System.currentTimeMillis());
                data.put("method", "manual");

                attendanceRef.child(attUid).setValue(data);

                // Update local data to avoid re-fetching from Firebase
                state.isPresent = true;
                state.method = "manual";

                presentCount++;
                manualCount++;
            }
        }

        absentCount = totalStudents - presentCount;
        updateSummaryBar();
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "Marked " + selected.size() + " students present", Toast.LENGTH_SHORT).show();
    }

    private void confirmBatchMarkAbsent(List<StudentAttendanceAdapter.StudentAttendanceState> selected, ActionMode mode) {
        new AlertDialog.Builder(this)
                .setTitle("Remove attendance for " + selected.size() + " students?")
                .setMessage("This will delete their attendance records.")
                .setPositiveButton("Remove", (dialog, which) -> {
                    batchMarkAbsent(selected);
                    mode.finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void batchMarkAbsent(List<StudentAttendanceAdapter.StudentAttendanceState> selected) {
        DatabaseReference attendanceRef = FirebaseDatabase.getInstance().getReference("attendance").child(classId).child(currentDate);

        for (StudentAttendanceAdapter.StudentAttendanceState state : selected) {
            if (state.isPresent) {
                String attUid = (state.student.getAuthUid() != null && !state.student.getAuthUid().isEmpty()) ? state.student.getAuthUid() : state.student.getUid();

                attendanceRef.child(attUid).removeValue();

                state.isPresent = false;

                presentCount--;
                if ("qr".equals(state.method)) {
                    qrCount--;
                } else if ("manual".equals(state.method)) {
                    manualCount--;
                }
                state.method = null;
            }
        }

        absentCount = totalStudents - presentCount;
        updateSummaryBar();
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "Marked " + selected.size() + " students absent", Toast.LENGTH_SHORT).show();
    }
}
