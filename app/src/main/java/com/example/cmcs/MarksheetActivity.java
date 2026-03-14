package com.example.cmcs;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.cmcs.db.MarksheetDao;
import com.example.cmcs.db.MarksheetDatabase;
import com.example.cmcs.db.MarksheetEntity;
import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MarksheetActivity — Phase 3
 *
 * ALL Room/DAO calls run on a single background executor thread. No
 * allowMainThreadQueries(). No AsyncTask. No Thread.sleep().
 *
 * Year → max semesters: Year 1=2, Year 2=4, Year 3=6, Year 4=8. Files:
 * getFilesDir()/marksheets/semester_X.<ext>
 */
public class MarksheetActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────
    private ProgressBar loadingSpinner;
    private LinearLayout containerSemesters;
    private TextView tvLabel;

    // ── Room ──────────────────────────────────────────────────────────────
    private MarksheetDao dao;
    /**
     * Single-threaded executor — ALL DAO calls must go through this.
     */
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    // ── State ─────────────────────────────────────────────────────────────
    /**
     * Semester number the user is currently picking a file for.
     */
    private int pendingSemester = -1;
    private int maxSemesters = 0;

    // ── File picker ───────────────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> filePicker
            = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                        && result.getData() != null
                        && pendingSemester > 0) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                saveFileForSemester(uri, pendingSemester);
                            }
                        }
                        pendingSemester = -1;
                    });

    private static final SimpleDateFormat DATE_FMT
            = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_marksheet);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        loadingSpinner = findViewById(R.id.marksheet_loading);
        containerSemesters = findViewById(R.id.container_semesters);
        tvLabel = findViewById(R.id.tv_marksheet_label);

        setupToolbar();

        dao = MarksheetDatabase.getInstance(this).marksheetDao();

        loadYearFromFirebase();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Toolbar
    // ─────────────────────────────────────────────────────────────────────
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.marksheet_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Firebase — read student year
    // ─────────────────────────────────────────────────────────────────────
    private void loadYearFromFirebase() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showError("Not signed in.");
            return;
        }

        FirebaseDatabase.getInstance()
                .getReference("users")
                .child(user.getUid())
                .child("year")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int year = 2; // safe default
                        String yearStr = snapshot.getValue(String.class);
                        if (yearStr != null && !yearStr.isEmpty()) {
                            try {
                                year = Integer.parseInt(yearStr.trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        year = Math.max(1, Math.min(4, year));   // clamp 1-4
                        maxSemesters = year * 2;
                        buildSemesterCards(maxSemesters);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        maxSemesters = 8;                         // show all on failure
                        buildSemesterCards(maxSemesters);
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Build cards — DB read on executor, UI update on main thread
    // ─────────────────────────────────────────────────────────────────────
    private void buildSemesterCards(int count) {
        dbExecutor.execute(() -> {
            // Read all records off-thread
            final MarksheetEntity[] records = new MarksheetEntity[count + 1];
            for (int s = 1; s <= count; s++) {
                records[s] = dao.getMarksheetForSemester(s);   // ← background thread
            }

            runOnUiThread(() -> {
                loadingSpinner.setVisibility(View.GONE);
                tvLabel.setVisibility(View.VISIBLE);
                containerSemesters.setVisibility(View.VISIBLE);
                containerSemesters.removeAllViews();

                for (int sem = 1; sem <= count; sem++) {
                    addSemesterCard(sem, records[sem]);
                }
            });
        });
    }

    /**
     * Inflates a single semester card and attaches it to the container.
     */
    private void addSemesterCard(int semester, MarksheetEntity record) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(params);
        card.setCardElevation(dpToPx(3));
        card.setRadius(dpToPx(14));
        card.setCardBackgroundColor(
                getResources().getColor(R.color.surfaceColor, null));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        inner.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Semester label
        TextView tvSem = new TextView(this);
        tvSem.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvSem.setText("Semester " + semester);
        tvSem.setTextColor(getResources().getColor(R.color.textPrimary, null));
        tvSem.setTextSize(15);
        tvSem.setTypeface(tvSem.getTypeface(), android.graphics.Typeface.BOLD);

        // Status label
        TextView tvStatus = new TextView(this);
        tvStatus.setPadding(dpToPx(8), 0, 0, 0);
        if (record != null) {
            tvStatus.setText("✓ " + record.fileName);
            tvStatus.setTextColor(
                    getResources().getColor(android.R.color.holo_green_dark, null));
            tvStatus.setTextSize(11);
            tvStatus.setMaxLines(1);
            tvStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        } else {
            tvStatus.setText("No file");
            tvStatus.setTextColor(
                    getResources().getColor(R.color.textSecondary, null));
            tvStatus.setTextSize(12);
        }

        inner.addView(tvSem);
        inner.addView(tvStatus);
        card.addView(inner);

        // ── Click: DB read MUST happen on executor ──────────────────────
        card.setOnClickListener(v -> {
            dbExecutor.execute(() -> {
                MarksheetEntity current
                        = dao.getMarksheetForSemester(semester);  // ← background thread
                runOnUiThread(() -> {
                    if (current == null) {
                        openFilePicker(semester);
                    } else {
                        showActionDialog(semester, current);
                    }
                });
            });
        });

        containerSemesters.addView(card);
    }

    // ─────────────────────────────────────────────────────────────────────
    // File pick → copy → Room insert (executor)
    // ─────────────────────────────────────────────────────────────────────
    private void openFilePicker(int semester) {
        pendingSemester = semester;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "application/pdf",
            "image/jpeg", "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        });
        filePicker.launch(intent);
    }

    /**
     * Copy picked file to private store and insert Room record — all on
     * executor.
     */
    private void saveFileForSemester(Uri uri, int semester) {
        dbExecutor.execute(() -> {                              // ← background thread
            try {
                String displayName = resolveFileName(uri);
                String ext = extensionFrom(displayName);

                File dir = new File(getFilesDir(), "marksheets");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                File dest = new File(dir, "semester_" + semester + "." + ext);

                try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(dest)) {
                    if (in == null) {
                        throw new IOException("Cannot open URI");
                    }
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        out.write(buf, 0, read);
                    }
                }

                MarksheetEntity entity = new MarksheetEntity();
                entity.semesterNumber = semester;
                entity.filePath = dest.getAbsolutePath();
                entity.fileName = displayName;
                entity.uploadedAt = System.currentTimeMillis();
                dao.insertMarksheet(entity);                    // ← background thread

                runOnUiThread(() -> {
                    Toast.makeText(this,
                            "Saved: Semester " + semester,
                            Toast.LENGTH_SHORT).show();
                    refreshCards();
                });

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this,
                        "Failed to save file: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Action dialog (View / Replace / Delete)
    // ─────────────────────────────────────────────────────────────────────
    /**
     * Called from main thread — dialog shown on main thread, DB ops in
     * executor.
     */
    private void showActionDialog(int semester, MarksheetEntity record) {
        String dateStr = DATE_FMT.format(new Date(record.uploadedAt));
        new AlertDialog.Builder(this)
                .setTitle("Semester " + semester + " Marksheet")
                .setMessage("File: " + record.fileName + "\nSaved: " + dateStr)
                .setPositiveButton("View", (d, w) -> viewFile(record))
                .setNeutralButton("Replace", (d, w) -> openFilePicker(semester))
                .setNegativeButton("Delete", (d, w) -> deleteFile(semester, record))
                .show();
    }

    /**
     * Open file with the system viewer via FileProvider.
     */
    private void viewFile(MarksheetEntity record) {
        File file = new File(record.filePath);
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileUri, getMimeType(record.fileName));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Open with…"));
    }

    /**
     * Delete local file + Room record on executor, then refresh.
     */
    private void deleteFile(int semester, MarksheetEntity record) {
        dbExecutor.execute(() -> {                              // ← background thread
            File file = new File(record.filePath);
            if (file.exists()) //noinspection ResultOfMethodCallIgnored
            {
                file.delete();
            }
            dao.deleteMarksheetForSemester(semester);          // ← background thread

            runOnUiThread(() -> {
                Toast.makeText(this,
                        "Deleted semester " + semester + " marksheet",
                        Toast.LENGTH_SHORT).show();
                refreshCards();
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────
    private void refreshCards() {
        if (maxSemesters > 0) {
            buildSemesterCards(maxSemesters);
        }
    }

    private String resolveFileName(Uri uri) {
        String name = "marksheet";
        try (Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    name = cursor.getString(idx);
                }
            }
        }
        return name;
    }

    private String extensionFrom(String name) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0 && dot < name.length() - 1)
                ? name.substring(dot + 1) : "bin";
    }

    private String getMimeType(String name) {
        switch (extensionFrom(name).toLowerCase(Locale.ROOT)) {
            case "pdf":
                return "application/pdf";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default:
                return "*/*";
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
