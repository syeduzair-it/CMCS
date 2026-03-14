package com.example.cmcs;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Stack;

import com.example.cmcs.adapters.StudentSwipeAdapter;
import com.example.cmcs.models.StudentModel;
import com.example.cmcs.utils.WindowInsetsHelper;

public class AttendanceSessionActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvDate, tvCountdownTimer, tvSessionExpired, tvPresentTitle, tvPresentCount;
    private MaterialButtonToggleGroup toggleGroupMode;
    private LinearLayout containerQrMode, containerManualMode;
    private ImageView ivQrCode;
    private ProgressBar progressBarQr;
    private MaterialButton btnViewStudentList;

    private String classId;
    private String parsedCourse = "";
    private String parsedYear = "";
    private String currentDate;
    private String teacherUid;
    private String teacherName = "Teacher";
    private int totalStudents = 0;

    // Swipe UI Components
    private RecyclerView rvStudentCards;
    private MaterialButton btnUndo;
    private StudentSwipeAdapter studentSwipeAdapter;
    private List<StudentModel> studentList;

    // Undo State
    private Stack<SwipeAction> swipeHistory = new Stack<>();

    private static class SwipeAction {

        StudentModel student;
        boolean wasPresent;
        int index;

        SwipeAction(StudentModel student, boolean wasPresent, int index) {
            this.student = student;
            this.wasPresent = wasPresent;
            this.index = index;
        }
    }

    // Manual Completion State
    private LinearLayout manualCompletionLayout;
    private TextView tvManualPresentCount, tvManualAbsentCount;
    private boolean isQrProcessing = false;

    private CountDownTimer countDownTimer;
    private DatabaseReference sessionRef;
    private String currentSessionId;
    private DatabaseReference attendanceRef;
    private ValueEventListener attendanceListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_session);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        toolbar = findViewById(R.id.toolbarAttendanceSession);
        tvDate = findViewById(R.id.tvDate);
        tvCountdownTimer = findViewById(R.id.tvCountdownTimer);
        tvSessionExpired = findViewById(R.id.tvSessionExpired);
        tvPresentTitle = findViewById(R.id.tvPresentTitle);
        tvPresentCount = findViewById(R.id.tvPresentCount);
        toggleGroupMode = findViewById(R.id.toggleGroupMode);
        containerQrMode = findViewById(R.id.containerQrMode);
        containerManualMode = findViewById(R.id.containerManualMode);
        ivQrCode = findViewById(R.id.ivQrCode);
        progressBarQr = findViewById(R.id.progressBarQr);
        btnViewStudentList = findViewById(R.id.btnViewStudentList);
        rvStudentCards = findViewById(R.id.rvStudentCards);
        btnUndo = findViewById(R.id.btnUndo);

        manualCompletionLayout = findViewById(R.id.manualCompletionLayout);
        tvManualPresentCount = findViewById(R.id.tvManualPresentCount);
        tvManualAbsentCount = findViewById(R.id.tvManualAbsentCount);

        // Default Activity State
        toggleGroupMode.check(R.id.btnManualMode);
        containerQrMode.setVisibility(View.GONE);
        containerManualMode.setVisibility(View.VISIBLE);

        studentList = new ArrayList<>();
        studentSwipeAdapter = new StudentSwipeAdapter(this, studentList);
        rvStudentCards.setLayoutManager(new LinearLayoutManager(this) {
            @Override
            public boolean canScrollVertically() {
                return false;
            }
        });
        rvStudentCards.setItemAnimator(null);
        rvStudentCards.setAdapter(studentSwipeAdapter);

        setupSwipeGestures();

        btnUndo.setOnClickListener(v -> undoLastSwipe());

        classId = getIntent().getStringExtra("classId");
        if (classId == null) {
            classId = "SESSION";
        } else {
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
        }
        toolbar.setTitle(classId);
        toolbar.setNavigationOnClickListener(v -> finish());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        currentDate = sdf.format(new Date());
        tvDate.setText("Date: " + currentDate);

        toggleGroupMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnQrMode) {
                    if (isQrProcessing) {
                        return;
                    }
                    showQrConfirmationDialog();
                } else if (checkedId == R.id.btnManualMode) {
                    containerQrMode.setVisibility(View.GONE);
                    containerManualMode.setVisibility(View.VISIBLE);
                }
            }
        });

        btnViewStudentList.setOnClickListener(v -> {
            Intent intent = new Intent(AttendanceSessionActivity.this, StudentListAttendanceActivity.class);
            intent.putExtra("classId", classId);
            intent.putExtra("date", currentDate);
            startActivity(intent);
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            teacherUid = user.getUid();
            fetchTeacherName();
        } else {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            finish();
        }

        loadTotalStudentCount();
        loadStudentsForManualAttendance();
    }

    private void fetchTeacherName() {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(teacherUid);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChild("name")) {
                    teacherName = snapshot.child("name").getValue(String.class);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void showQrConfirmationDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Generate QR Attendance?")
                .setMessage("This QR will be valid for 15 minutes.")
                .setPositiveButton("Generate", (dialog, which) -> {
                    checkAttendanceCountAndGenerate();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    toggleGroupMode.check(R.id.btnManualMode);
                })
                .setCancelable(false)
                .show();
    }

    private void checkAttendanceCountAndGenerate() {
        if (isQrProcessing) {
            return;
        }
        isQrProcessing = true;
        progressBarQr.setVisibility(View.VISIBLE);

        if (attendanceRef == null) {
            attendanceRef = FirebaseDatabase.getInstance().getReference("attendance").child(classId).child(currentDate);
        }

        attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                if (count > 0) {
                    long qrCount = 0;
                    long manualCount = 0;
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        String method = ds.child("method").getValue(String.class);
                        if ("qr".equals(method)) {
                            qrCount++;
                        } else if ("manual".equals(method)) {
                            manualCount++;
                        }
                    }
                    long totalPresent = qrCount + manualCount;

                    isQrProcessing = false;
                    progressBarQr.setVisibility(View.GONE);
                    new androidx.appcompat.app.AlertDialog.Builder(AttendanceSessionActivity.this)
                            .setTitle("Attendance already started today.")
                            .setMessage("Total Present: " + totalPresent + " / " + totalStudents + "\nQR Attendance: " + qrCount + "\nManual Attendance: " + manualCount + "\n\nPlease continue using manual attendance.")
                            .setPositiveButton("OK", (dialog, which) -> {
                                toggleGroupMode.check(R.id.btnManualMode);
                            })
                            .setCancelable(false)
                            .show();
                } else {
                    checkAndSetupSession();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                isQrProcessing = false;
                progressBarQr.setVisibility(View.GONE);
                Toast.makeText(AttendanceSessionActivity.this, "Failed to verify attendance data", Toast.LENGTH_SHORT).show();
                toggleGroupMode.check(R.id.btnManualMode);
            }
        });
    }

    private void checkAndSetupSession() {
        sessionRef = FirebaseDatabase.getInstance().getReference("attendance_sessions").child(classId).child(currentDate);
        Query activeQuery = sessionRef.orderByChild("active").equalTo(true).limitToFirst(1);
        activeQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long currentTime = System.currentTimeMillis();
                boolean reused = false;

                if (snapshot.exists()) {
                    for (DataSnapshot sessionSnap : snapshot.getChildren()) {
                        Long expiryTimeObj = sessionSnap.child("expiryTime").getValue(Long.class);
                        long expiryTime = expiryTimeObj != null ? expiryTimeObj : 0;

                        if (expiryTime > currentTime) {
                            currentSessionId = sessionSnap.getKey();
                            String token = sessionSnap.child("token").getValue(String.class);
                            generateQrCodeAndStartTimer(token, expiryTime);
                            reused = true;
                        } else {
                            sessionSnap.getRef().child("active").setValue(false);
                        }
                    }
                }

                if (!reused) {
                    createNewSession(currentTime);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                isQrProcessing = false;
                progressBarQr.setVisibility(View.GONE);
                Toast.makeText(AttendanceSessionActivity.this, "Failed to check sessions", Toast.LENGTH_SHORT).show();
                toggleGroupMode.check(R.id.btnManualMode);
            }
        });
    }

    private void createNewSession(long currentTime) {
        currentSessionId = String.valueOf(currentTime);
        String token = UUID.randomUUID().toString();
        long expiryTime = currentTime + (15 * 60 * 1000); // 15 mins

        HashMap<String, Object> sessionData = new HashMap<>();
        sessionData.put("token", token);
        sessionData.put("startTime", currentTime);
        sessionData.put("expiryTime", expiryTime);
        sessionData.put("teacherUid", teacherUid);
        sessionData.put("teacherName", teacherName);
        sessionData.put("active", true);

        sessionRef.child(currentSessionId).setValue(sessionData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                generateQrCodeAndStartTimer(token, expiryTime);
            } else {
                isQrProcessing = false;
                progressBarQr.setVisibility(View.GONE);
                Toast.makeText(AttendanceSessionActivity.this, "Failed to create session", Toast.LENGTH_SHORT).show();
                toggleGroupMode.check(R.id.btnManualMode);
            }
        });
    }

    private void generateQrCodeAndStartTimer(String token, long expiryTime) {
        String qrData = "cmcs_attendance|class=" + classId + "|date=" + currentDate + "|sessionId=" + currentSessionId + "|token=" + token;

        try {
            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix bitMatrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 800, 800);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            ivQrCode.setImageBitmap(bmp);
        } catch (WriterException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate QR", Toast.LENGTH_SHORT).show();
        }

        isQrProcessing = false;
        containerQrMode.setVisibility(View.VISIBLE);
        containerManualMode.setVisibility(View.GONE);
        progressBarQr.setVisibility(View.GONE);
        ivQrCode.setVisibility(View.VISIBLE);
        tvSessionExpired.setVisibility(View.GONE);

        startAttendanceListener();

        long timeLeft = expiryTime - System.currentTimeMillis();
        if (timeLeft > 0) {
            startTimer(timeLeft);
        } else {
            expireSession();
        }
    }

    private void startTimer(long durationMs) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = (millisUntilFinished / 1000) / 60;
                long seconds = (millisUntilFinished / 1000) % 60;
                tvCountdownTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
            }

            @Override
            public void onFinish() {
                expireSession();
            }
        }.start();
    }

    private void expireSession() {
        tvCountdownTimer.setText("00:00");
        ivQrCode.setVisibility(View.GONE);
        tvSessionExpired.setVisibility(View.VISIBLE);

        stopAttendanceListener();

        if (currentSessionId != null && sessionRef != null) {
            sessionRef.child(currentSessionId).child("active").setValue(false);
        }

        if (attendanceRef == null) {
            attendanceRef = FirebaseDatabase.getInstance().getReference("attendance").child(classId).child(currentDate);
        }
        attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long qrCount = 0;
                long manualCount = 0;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String method = ds.child("method").getValue(String.class);
                    if ("qr".equals(method)) {
                        qrCount++;
                    } else if ("manual".equals(method)) {
                        manualCount++;
                    }
                }
                long totalPresent = qrCount + manualCount;
                long absentCount = totalStudents - totalPresent;

                if (!isFinishing()) {
                    new androidx.appcompat.app.AlertDialog.Builder(AttendanceSessionActivity.this)
                            .setTitle("Attendance Session Completed")
                            .setMessage("Total Present: " + totalPresent + "\nQR Attendance: " + qrCount + "\nManual Attendance: " + manualCount + "\nAbsent: " + absentCount)
                            .setPositiveButton("OK", null)
                            .setCancelable(false)
                            .show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        stopAttendanceListener();
    }

    private void startAttendanceListener() {
        if (attendanceRef == null) {
            attendanceRef = FirebaseDatabase.getInstance().getReference("attendance").child(classId).child(currentDate);
        }
        if (attendanceListener == null) {
            attendanceListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    long presentCount = snapshot.getChildrenCount();
                    tvPresentCount.setText(presentCount + " / " + (totalStudents > 0 ? totalStudents : "?"));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            };
            attendanceRef.addValueEventListener(attendanceListener);
        }
    }

    private void stopAttendanceListener() {
        if (attendanceRef != null && attendanceListener != null) {
            attendanceRef.removeEventListener(attendanceListener);
            attendanceListener = null;
        }
    }

    private void loadTotalStudentCount() {
        if (parsedCourse.isEmpty() || parsedYear.isEmpty()) {
            return;
        }

        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("students");
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = 0;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StudentModel student = ds.getValue(StudentModel.class);
                    if (student != null) {
                        if (parsedCourse.equalsIgnoreCase(student.getCourse()) && parsedYear.equalsIgnoreCase(student.getYear())) {
                            count++;
                        }
                    }
                }
                totalStudents = count;
                // Force an update to the UI if present count already parsed it previously
                if (attendanceRef != null && attendanceListener != null) {
                    attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            long presentCount = snapshot.getChildrenCount();
                            tvPresentCount.setText(presentCount + " / " + totalStudents);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                        }
                    });
                } else {
                    tvPresentCount.setText("0 / " + totalStudents);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void loadStudentsForManualAttendance() {
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("students");
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<StudentModel> tempStudents = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StudentModel student = ds.getValue(StudentModel.class);
                    if (student != null) {
                        if (student.getUid() == null) {
                            student.setUid(ds.getKey());
                        }
                        if (parsedCourse.equalsIgnoreCase(student.getCourse()) && parsedYear.equalsIgnoreCase(student.getYear())) {
                            tempStudents.add(student);
                        }
                    }
                }

                Collections.sort(tempStudents, (s1, s2) -> {
                    try {
                        int r1 = Integer.parseInt(s1.getRollNumber());
                        int r2 = Integer.parseInt(s2.getRollNumber());
                        return Integer.compare(r1, r2);
                    } catch (Exception e) {
                        return 0;
                    }
                });

                filterAlreadyMarkedStudents(tempStudents);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void filterAlreadyMarkedStudents(List<StudentModel> allStudents) {
        DatabaseReference attendanceCheckRef = FirebaseDatabase.getInstance()
                .getReference("attendance")
                .child(classId).child(currentDate);

        attendanceCheckRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                studentList.clear();
                for (StudentModel student : allStudents) {
                    String attUid = student.getAuthUid() != null ? student.getAuthUid() : student.getUid();
                    if (!snapshot.hasChild(attUid)) {
                        studentList.add(student);
                    }
                }
                studentSwipeAdapter.notifyDataSetChanged();

                if (studentList.size() > 0) {
                    rvStudentCards.setVisibility(View.VISIBLE);
                    manualCompletionLayout.setVisibility(View.GONE);
                } else {
                    rvStudentCards.setVisibility(View.GONE);
                    manualCompletionLayout.setVisibility(View.VISIBLE);
                    checkManualAttendanceCompletion();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void setupSwipeGestures() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();

                StudentModel swipedStudent;
                if (studentList.size() == 1) {
                    swipedStudent = studentList.remove(position);
                    rvStudentCards.setVisibility(View.GONE);
                    manualCompletionLayout.setVisibility(View.VISIBLE);
                } else {
                    swipedStudent = studentSwipeAdapter.removeItem(position);
                }

                boolean wasPresent = (direction == ItemTouchHelper.RIGHT);
                swipeHistory.push(new SwipeAction(swipedStudent, wasPresent, position));

                if (wasPresent) {
                    markManualAttendance(swipedStudent);
                } else {
                    btnUndo.setVisibility(View.VISIBLE);
                    checkManualAttendanceCompletion();
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

                View itemView = viewHolder.itemView;
                float rotation = dX * 0.05f; // Slight tilt
                itemView.setRotation(rotation);

                if (dX > 0) {
                    // Swiping right (Present -> Green)
                    itemView.setBackgroundColor(Color.parseColor("#81C784"));
                } else if (dX < 0) {
                    // Swiping left (Absent -> Red)
                    itemView.setBackgroundColor(Color.parseColor("#E57373"));
                } else {
                    itemView.setBackgroundColor(Color.WHITE);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setRotation(0f); // Reset rotation on cancel
                viewHolder.itemView.setBackgroundColor(Color.WHITE);
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvStudentCards);
    }

    private void markManualAttendance(StudentModel student) {
        String uidToUse = student.getAuthUid() != null ? student.getAuthUid() : student.getUid();
        DatabaseReference studentAttRef = FirebaseDatabase.getInstance()
                .getReference("attendance")
                .child(classId).child(currentDate).child(uidToUse);

        HashMap<String, Object> data = new HashMap<>();
        data.put("name", student.getName());
        data.put("timestamp", System.currentTimeMillis());
        data.put("method", "manual");

        studentAttRef.setValue(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                btnUndo.setVisibility(View.VISIBLE);
                Toast.makeText(this, student.getName() + " marked present", Toast.LENGTH_SHORT).show();
                checkManualAttendanceCompletion();
            }
        });
    }

    private void checkManualAttendanceCompletion() {
        if (studentList.isEmpty()) {
            rvStudentCards.setVisibility(View.GONE);
            manualCompletionLayout.setVisibility(View.VISIBLE);

            if (attendanceRef == null) {
                attendanceRef = FirebaseDatabase.getInstance().getReference("attendance").child(classId).child(currentDate);
            }
            attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    long presentCount = snapshot.getChildrenCount();
                    long absentCount = totalStudents - presentCount;

                    tvManualPresentCount.setText("Present: " + presentCount);
                    tvManualAbsentCount.setText("Absent: " + absentCount);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });
        }
    }

    private void undoLastSwipe() {
        if (!swipeHistory.isEmpty()) {
            SwipeAction lastAction = swipeHistory.pop();

            manualCompletionLayout.setVisibility(View.GONE);
            rvStudentCards.setVisibility(View.VISIBLE);

            if (studentList.isEmpty()) {
                studentList.add(lastAction.index, lastAction.student);
                studentSwipeAdapter.notifyItemInserted(lastAction.index);
            } else {
                studentSwipeAdapter.restoreItem(lastAction.student, lastAction.index);
            }
            rvStudentCards.scrollToPosition(lastAction.index);

            if (lastAction.wasPresent) {
                String uidToUse = lastAction.student.getAuthUid() != null ? lastAction.student.getAuthUid() : lastAction.student.getUid();
                DatabaseReference studentAttRef = FirebaseDatabase.getInstance()
                        .getReference("attendance")
                        .child(classId).child(currentDate).child(uidToUse);

                studentAttRef.removeValue().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Undo successful", Toast.LENGTH_SHORT).show();
                        if (swipeHistory.isEmpty()) {
                            btnUndo.setVisibility(View.GONE);
                        }
                    }
                });
            } else {
                Toast.makeText(this, "Undo successful", Toast.LENGTH_SHORT).show();
                if (swipeHistory.isEmpty()) {
                    btnUndo.setVisibility(View.GONE);
                }
            }
        }
    }
}
