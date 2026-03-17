package com.example.cmcs;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.TimeZone;

import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;
import androidx.core.content.ContextCompat;
import android.graphics.drawable.Drawable;
import android.text.style.ForegroundColorSpan;
import com.example.cmcs.utils.WindowInsetsHelper;

public class MyAttendanceActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private ProgressBar progressBar;
    private TextView tvNoData;
    private LinearLayout layoutAttendanceData;

    private PieChart pieChartAttendance;
    private TextView tvTotalSessions;
    private TextView tvPresentCount;
    private TextView tvAbsentCount;
    private TextView tvAttendancePercentage;

    private MaterialCalendarView calendarAttendance;

    private String currentUid;
    private int totalSessions = 0;
    private int presentCount = 0;
    private int absentCount = 0;

    private HashSet<CalendarDay> presentDates = new HashSet<>();
    private HashSet<CalendarDay> absentDates = new HashSet<>();

    private ValueEventListener attendanceValueListener;
    private DatabaseReference attendanceDbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_attendance);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        toolbar = findViewById(R.id.toolbarMyAttendance);
        progressBar = findViewById(R.id.progressBarMyAttendance);
        tvNoData = findViewById(R.id.tvNoData);
        layoutAttendanceData = findViewById(R.id.layoutAttendanceData);

        pieChartAttendance = findViewById(R.id.pieChartAttendance);
        tvTotalSessions = findViewById(R.id.tvTotalSessions);
        tvPresentCount = findViewById(R.id.tvPresentCount);
        tvAbsentCount = findViewById(R.id.tvAbsentCount);
        tvAttendancePercentage = findViewById(R.id.tvAttendancePercentage);

        calendarAttendance = findViewById(R.id.calendarAttendance);

        toolbar.setNavigationOnClickListener(v -> finish());

        // Setup chart appearance
        pieChartAttendance.getDescription().setEnabled(false);
        pieChartAttendance.setDrawEntryLabels(false);
        pieChartAttendance.setCenterText("Attendance");
        pieChartAttendance.setCenterTextSize(16f);
        pieChartAttendance.setHoleRadius(50f);
        pieChartAttendance.setTransparentCircleRadius(55f);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showNoData();
            return;
        }
        currentUid = user.getUid();

        fetchUserDataAndAttendance();
    }

    private void fetchUserDataAndAttendance() {
        progressBar.setVisibility(View.VISIBLE);
        layoutAttendanceData.setVisibility(View.GONE);
        tvNoData.setVisibility(View.GONE);

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUid);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChild("course") && snapshot.hasChild("year")) {
                    String course = snapshot.child("course").getValue(String.class);
                    String yearString = snapshot.child("year").getValue(String.class);

                    if (course != null && yearString != null) {
                        String classId = buildClassId(course, yearString);
                        Log.d("MyAttendance", "ClassId=" + classId);

                        if (!classId.isEmpty()) {
                            fetchAttendance(classId);
                            return;
                        }
                    }
                }
                showNoData();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showNoData();
            }
        });
    }

    private String buildClassId(String course, String yearString) {
        String yearNumber = "";
        switch (yearString.toLowerCase()) {
            case "1st_year":
                yearNumber = "1";
                break;
            case "2nd_year":
                yearNumber = "2";
                break;
            case "3rd_year":
                yearNumber = "3";
                break;
            case "4th_year":
                yearNumber = "4";
                break;
            case "5th_year":
                yearNumber = "5";
                break;
        }

        if (yearNumber.isEmpty()) {
            return "";
        }

        return course.toUpperCase() + " " + yearNumber;
    }

    private void fetchAttendance(String classId) {
        // IST timezone — must match the timezone used when writing attendance
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

        String todayDate = sdf.format(new Date());
        Log.d("DATE_DEBUG", "Today date (IST): " + todayDate);
        Log.d("PATH_DEBUG", "Reading: attendance/" + classId);

        attendanceDbRef = FirebaseDatabase.getInstance().getReference("attendance").child(classId);

        // ValueEventListener so UI updates in real time (including today's data)
        attendanceValueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);

                if (!snapshot.exists()) {
                    showNoData();
                    return;
                }

                Log.d("DATE_DEBUG", "Attendance node children: " + snapshot.getChildrenCount());

                presentCount = 0;
                totalSessions = 0;
                presentDates.clear();
                absentDates.clear();

                for (DataSnapshot dateSnapshot : snapshot.getChildren()) {
                    if (dateSnapshot.getChildrenCount() == 0) {
                        continue; // ignore empty session
                    }

                    totalSessions++;

                    String dateStr = dateSnapshot.getKey();
                    Log.d("DATE_DEBUG", "Found session date: " + dateStr
                            + " | hasMyUid=" + dateSnapshot.hasChild(currentUid));

                    CalendarDay calendarDay = null;
                    if (dateStr != null) {
                        try {
                            Date date = sdf.parse(dateStr);
                            if (date != null) {
                                java.util.Calendar cal = java.util.Calendar.getInstance(
                                        TimeZone.getTimeZone("Asia/Kolkata"));
                                cal.setTime(date);
                                calendarDay = CalendarDay.from(cal);
                            }
                        } catch (ParseException e) {
                            Log.e("MyAttendance", "Date parse error: " + dateStr, e);
                        }
                    }

                    if (dateSnapshot.hasChild(currentUid)) {
                        presentCount++;
                        if (calendarDay != null) presentDates.add(calendarDay);
                    } else {
                        if (calendarDay != null) absentDates.add(calendarDay);
                    }
                }

                if (totalSessions == 0) {
                    showNoData();
                    return;
                }

                absentCount = totalSessions - presentCount;

                Log.d("MyAttendance", "TotalSessions=" + totalSessions
                        + " Present=" + presentCount + " Absent=" + absentCount);

                updateUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                showNoData();
            }
        };
        attendanceDbRef.addValueEventListener(attendanceValueListener);
    }

    private void updateUI() {
        tvNoData.setVisibility(View.GONE);
        layoutAttendanceData.setVisibility(View.VISIBLE);

        tvTotalSessions.setText(String.valueOf(totalSessions));
        tvPresentCount.setText(String.valueOf(presentCount));
        tvAbsentCount.setText(String.valueOf(absentCount));

        int percentage = (presentCount * 100) / totalSessions;
        tvAttendancePercentage.setText("Attendance: " + percentage + "%");

        ArrayList<PieEntry> entries = new ArrayList<>();
        if (presentCount > 0) {
            entries.add(new PieEntry(presentCount, "Present"));
        }
        if (absentCount > 0) {
            entries.add(new PieEntry(absentCount, "Absent"));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");

        // Define colors: Green for Present, Red for Absent
        ArrayList<Integer> colors = new ArrayList<>();
        if (presentCount > 0) {
            colors.add(android.graphics.Color.parseColor("#4CAF50"));
        }
        if (absentCount > 0) {
            colors.add(android.graphics.Color.parseColor("#F44336"));
        }
        dataSet.setColors(colors);

        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(android.graphics.Color.WHITE);

        PieData data = new PieData(dataSet);
        pieChartAttendance.setData(data);
        pieChartAttendance.animateY(1000);
        pieChartAttendance.invalidate(); // Refresh chart

        calendarAttendance.removeDecorators();
        calendarAttendance.addDecorator(new PresentDayDecorator(presentDates));
        calendarAttendance.addDecorator(new AbsentDayDecorator(absentDates));
    }

    private void showNoData() {
        progressBar.setVisibility(View.GONE);
        layoutAttendanceData.setVisibility(View.GONE);
        tvNoData.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (attendanceDbRef != null && attendanceValueListener != null) {
            attendanceDbRef.removeEventListener(attendanceValueListener);
        }
    }

    private class PresentDayDecorator implements DayViewDecorator {

        private final HashSet<CalendarDay> dates;
        private final Drawable drawable;

        public PresentDayDecorator(HashSet<CalendarDay> dates) {
            this.dates = dates;
            this.drawable = ContextCompat.getDrawable(MyAttendanceActivity.this, R.drawable.bg_present_day);
        }

        @Override
        public boolean shouldDecorate(CalendarDay day) {
            return dates.contains(day);
        }

        @Override
        public void decorate(DayViewFacade view) {
            if (drawable != null) {
                view.setBackgroundDrawable(drawable);
            }
            view.addSpan(new ForegroundColorSpan(android.graphics.Color.WHITE));
        }
    }

    private class AbsentDayDecorator implements DayViewDecorator {

        private final HashSet<CalendarDay> dates;
        private final Drawable drawable;

        public AbsentDayDecorator(HashSet<CalendarDay> dates) {
            this.dates = dates;
            this.drawable = ContextCompat.getDrawable(MyAttendanceActivity.this, R.drawable.bg_absent_day);
        }

        @Override
        public boolean shouldDecorate(CalendarDay day) {
            return dates.contains(day);
        }

        @Override
        public void decorate(DayViewFacade view) {
            if (drawable != null) {
                view.setBackgroundDrawable(drawable);
            }
            view.addSpan(new ForegroundColorSpan(android.graphics.Color.WHITE));
        }
    }
}
