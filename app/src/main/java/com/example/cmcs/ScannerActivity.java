package com.example.cmcs;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 101;
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private boolean isProcessing = false;
    private String studentName = "Student";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        previewView = findViewById(R.id.previewView);
        cameraExecutor = Executors.newSingleThreadExecutor();

        fetchStudentName();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE
            );
        }
    }

    private void fetchStudentName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists() && snapshot.hasChild("name")) {
                        studentName = snapshot.child("name").getValue(String.class);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR code", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void processImageProxy(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null || isProcessing) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();

        BarcodeScanner scanner = BarcodeScanning.getClient(options);

        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String rawValue = barcode.getRawValue();
                        if (rawValue != null && rawValue.startsWith("cmcs_attendance")) {
                            isProcessing = true;
                            processSchoolQrCode(rawValue);
                            break; // only process the first valid hit
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Ignored
                })
                .addOnCompleteListener(task -> {
                    imageProxy.close();
                });
    }

    private void processSchoolQrCode(String qrData) {
        String[] parts = qrData.split("\\|");

        String classId = null;
        String date = null;
        String sessionId = null;
        String token = null;

        for (String part : parts) {
            if (part.startsWith("class=")) {
                classId = part.substring(6);
            } else if (part.startsWith("date=")) {
                date = part.substring(5);
            } else if (part.startsWith("sessionId=")) {
                sessionId = part.substring(10);
            } else if (part.startsWith("token=")) {
                token = part.substring(6);
            }
        }

        if (classId == null || date == null || sessionId == null || token == null) {
            isProcessing = false;
            return;
        }

        validateSessionAndMarkAttendance(classId, date, sessionId, token);
    }

    private void validateSessionAndMarkAttendance(String classId, String date, String sessionId, String token) {
        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("attendance_sessions")
                .child(classId).child(date).child(sessionId);

        sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long currentTime = System.currentTimeMillis();

                if (!snapshot.exists()) {
                    showErrorAndUnlock("Attendance session not found");
                    return;
                }

                Boolean active = snapshot.child("active").getValue(Boolean.class);
                Long expiryTime = snapshot.child("expiryTime").getValue(Long.class);
                String dbToken = snapshot.child("token").getValue(String.class);

                if (active == null || !active || expiryTime == null || expiryTime <= currentTime || !token.equals(dbToken)) {
                    Toast.makeText(ScannerActivity.this, "QR expired. Meet faculty member to mark today's attendance.", Toast.LENGTH_LONG).show();
                    // Keep isProcessing = true indefinitely to block rapid retry loops. Student has to re-open or faculty to fix.
                    return;
                }

                // Session valid. Proceed to duplicate check.
                checkIfAlreadyMarked(classId, date);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showErrorAndUnlock("Failed to validate session");
            }
        });
    }

    private void checkIfAlreadyMarked(String classId, String date) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showErrorAndUnlock("Not authenticated");
            return;
        }

        String studentUid = user.getUid();
        DatabaseReference attendanceRef = FirebaseDatabase.getInstance()
                .getReference("attendance")
                .child(classId).child(date).child(studentUid);

        attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    showErrorAndUnlock("Attendance already marked");
                } else {
                    markAttendance(attendanceRef);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showErrorAndUnlock("Failed to check duplicates");
            }
        });
    }

    private void markAttendance(DatabaseReference attendanceRef) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("name", studentName);
        data.put("timestamp", System.currentTimeMillis());
        data.put("method", "qr");

        attendanceRef.setValue(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                showSuccessDialog();
            } else {
                showErrorAndUnlock("Failed to mark attendance");
            }
        });
    }

    private void showErrorAndUnlock(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        // Give a short delay before un-locking so repeating toasts aren't spammed instantly
        previewView.postDelayed(() -> isProcessing = false, 2000);
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Success")
                .setMessage("Attendance marked successfully")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    finish();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
