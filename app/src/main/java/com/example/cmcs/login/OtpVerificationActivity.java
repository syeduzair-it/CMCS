package com.example.cmcs.login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cmcs.R;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.*;

import java.util.concurrent.TimeUnit;

public class OtpVerificationActivity extends AppCompatActivity {

    EditText otp1, otp2, otp3, otp4, otp5, otp6;
    Button verifyButton;
    String verificationId;
    FirebaseAuth mAuth;
    String phone, uid, studentId, department,course, year, gender;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.otp_verification);

        otp1 = findViewById(R.id.otp1);
        otp2 = findViewById(R.id.otp2);
        otp3 = findViewById(R.id.otp3);
        otp4 = findViewById(R.id.otp4);
        otp5 = findViewById(R.id.otp5);
        otp6 = findViewById(R.id.otp6);
        verifyButton = findViewById(R.id.submitOtpButton);
        mAuth = FirebaseAuth.getInstance();

        // Get phone and uid from intent
        phone = getIntent().getStringExtra("phone");
        uid = getIntent().getStringExtra("uid");
        studentId = getIntent().getStringExtra("studentId");
        department = getIntent().getStringExtra("department");
        course = getIntent().getStringExtra("course");
        year = getIntent().getStringExtra("year");
        gender = getIntent().getStringExtra("gender");

        sendOtp(phone);

        // Auto move cursor to next box
        setupOtpMovement();

        verifyButton.setOnClickListener(v -> {
            String code = otp1.getText().toString().trim() +
                    otp2.getText().toString().trim() +
                    otp3.getText().toString().trim() +
                    otp4.getText().toString().trim() +
                    otp5.getText().toString().trim() +
                    otp6.getText().toString().trim();

            if (code.length() == 6 && verificationId != null) {
                PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
                signInWithCredential(credential);
            } else {
                Toast.makeText(this, "Please enter all 6 digits", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendOtp(String phoneNumber) {
        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            @Override
                            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                                // Auto retrieved
                                signInWithCredential(credential);
                            }

                            @Override
                            public void onVerificationFailed(@NonNull FirebaseException e) {
                                Toast.makeText(OtpVerificationActivity.this, "Verification Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onCodeSent(@NonNull String verificationId_,
                                                   @NonNull PhoneAuthProvider.ForceResendingToken token) {
                                verificationId = verificationId_;
                                Toast.makeText(OtpVerificationActivity.this, "Code Sent", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void signInWithCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Verification Successful", Toast.LENGTH_SHORT).show();
                        // Redirect to main activity
                        Intent intent = new Intent(OtpVerificationActivity.this, SetPassword.class);
                        intent.putExtra("studentId", studentId);
                        intent.putExtra("phone", phone);
                        intent.putExtra("uid",uid);

                        intent.putExtra("department", department);
                        intent.putExtra("course", course);
                        intent.putExtra("year", year);
                        intent.putExtra("gender", gender);




                        startActivity(intent);
                    } else {
                        Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupOtpMovement() {
        EditText[] fields = {otp1, otp2, otp3, otp4, otp5, otp6};
        for (int i = 0; i < fields.length - 1; i++) {
            int finalI = i;
            fields[i].addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1) {
                        fields[finalI + 1].requestFocus();
                    }
                }

                @Override
                public void afterTextChanged(android.text.Editable s) { }
            });
        }
    }
}
