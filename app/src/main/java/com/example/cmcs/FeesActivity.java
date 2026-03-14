package com.example.cmcs;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * FeesActivity — Phase 2
 *
 * Student-only, read-only view of fee data stored at: fees/<studentUid>/
 * totalAmount : number paidAmount : number lastUpdated : long (epoch ms)
 *
 * No editing controls are present. Teachers who accidentally land here will see
 * the empty state.
 */
public class FeesActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────
    private ProgressBar loadingSpinner;
    private CardView cardSummary;
    private CardView cardProgress;
    private LinearLayout layoutEmpty;
    private TextView tvTotal;
    private TextView tvPaid;
    private TextView tvRemaining;
    private TextView tvLastUpdated;
    private TextView tvPercentage;
    private LinearProgressIndicator progressFees;

    // ── Firebase ──────────────────────────────────────────────────────────
    private DatabaseReference feesRef;
    private ValueEventListener feesListener;

    // ── Formatters ────────────────────────────────────────────────────────
    private static final NumberFormat CURRENCY
            = NumberFormat.getInstance(new Locale("en", "IN"));
    private static final SimpleDateFormat DATE_FMT
            = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fees);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        bindViews();
        setupToolbar();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showEmptyState();   // Not signed in — shouldn't happen, but guard anyway
            return;
        }

        feesRef = FirebaseDatabase.getInstance()
                .getReference("fees")
                .child(user.getUid());

        loadFees();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove listener to prevent memory leaks
        if (feesRef != null && feesListener != null) {
            feesRef.removeEventListener(feesListener);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // View binding
    // ─────────────────────────────────────────────────────────────────────
    private void bindViews() {
        loadingSpinner = findViewById(R.id.fees_loading);
        cardSummary = findViewById(R.id.card_fees_summary);
        cardProgress = findViewById(R.id.card_fees_progress);
        layoutEmpty = findViewById(R.id.layout_empty_state);
        tvTotal = findViewById(R.id.tv_total);
        tvPaid = findViewById(R.id.tv_paid);
        tvRemaining = findViewById(R.id.tv_remaining);
        tvLastUpdated = findViewById(R.id.tv_last_updated);
        tvPercentage = findViewById(R.id.tv_percentage);
        progressFees = findViewById(R.id.progress_fees);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Toolbar
    // ─────────────────────────────────────────────────────────────────────
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.fees_toolbar);
        setSupportActionBar(toolbar);
        // Navigation icon (back arrow) wired here — no ActionBarDrawerToggle needed
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Firebase data load
    // ─────────────────────────────────────────────────────────────────────
    private void loadFees() {
        showLoading(true);

        feesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                showLoading(false);

                // ── Null / missing node guard ─────────────────────────
                if (!snapshot.exists()) {
                    showEmptyState();
                    return;
                }

                Double totalRaw = null;
                Double paidRaw = null;

                // Reads as Long or Double depending on how Firebase stored it
                Object totalObj = snapshot.child("totalAmount").getValue();
                Object paidObj = snapshot.child("paidAmount").getValue();

                if (totalObj instanceof Number) {
                    totalRaw = ((Number) totalObj).doubleValue();
                }
                if (paidObj instanceof Number) {
                    paidRaw = ((Number) paidObj).doubleValue();
                }

                if (totalRaw == null || paidRaw == null) {
                    showEmptyState();
                    return;
                }

                double total = totalRaw;
                double paid = paidRaw;
                double remaining = Math.max(0, total - paid);          // clamp to 0
                int pct = total > 0
                        ? (int) Math.min(100, Math.round((paid / total) * 100))
                        : 0;

                // ── Last updated timestamp ────────────────────────────
                String lastUpdatedStr = "";
                Object tsObj = snapshot.child("lastUpdated").getValue();
                if (tsObj instanceof Number) {
                    long ts = ((Number) tsObj).longValue();
                    lastUpdatedStr = "Last updated: " + DATE_FMT.format(new Date(ts));
                }

                populateUI(total, paid, remaining, pct, lastUpdatedStr);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                showEmptyState();
            }
        };

        feesRef.addListenerForSingleValueEvent(feesListener);
    }

    // ─────────────────────────────────────────────────────────────────────
    // UI population
    // ─────────────────────────────────────────────────────────────────────
    private void populateUI(double total, double paid, double remaining,
            int pct, String lastUpdatedStr) {
        tvTotal.setText("₹ " + CURRENCY.format((long) total));
        tvPaid.setText("₹ " + CURRENCY.format((long) paid));
        tvRemaining.setText("₹ " + CURRENCY.format((long) remaining));

        if (!lastUpdatedStr.isEmpty()) {
            tvLastUpdated.setText(lastUpdatedStr);
            tvLastUpdated.setVisibility(View.VISIBLE);
        } else {
            tvLastUpdated.setVisibility(View.GONE);
        }

        tvPercentage.setText(pct + "%");
        progressFees.setProgressCompat(pct, true);   // animated update

        // Show content cards, hide empty state
        cardSummary.setVisibility(View.VISIBLE);
        cardProgress.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Visibility helpers
    // ─────────────────────────────────────────────────────────────────────
    private void showLoading(boolean loading) {
        loadingSpinner.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            cardSummary.setVisibility(View.GONE);
            cardProgress.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.GONE);
        }
    }

    private void showEmptyState() {
        loadingSpinner.setVisibility(View.GONE);
        cardSummary.setVisibility(View.GONE);
        cardProgress.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
    }
}
