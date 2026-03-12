package com.example.cmcs.adapters;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.cmcs.R;
import com.example.cmcs.models.FeatureModel;
import com.example.cmcs.models.FeatureSlideModel;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import me.relex.circleindicator.CircleIndicator3;

public class FeatureAdapter extends RecyclerView.Adapter<FeatureAdapter.FeatureVH> {

    private static final long AUTO_SCROLL_MS = 4000L;
    private final Context ctx;
    private final List<FeatureModel> features;
    private final boolean isTeacher;
    // Callback to let HomeFragment reload the features after deletion
    private final Runnable onDataChangedRunnable;

    public FeatureAdapter(Context ctx, List<FeatureModel> features, boolean isTeacher, Runnable onDataChangedRunnable) {
        this.ctx = ctx;
        this.features = features;
        this.isTeacher = isTeacher;
        this.onDataChangedRunnable = onDataChangedRunnable;
    }

    @NonNull
    @Override
    public FeatureVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_feature, parent, false);
        return new FeatureVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FeatureVH holder, int position) {
        FeatureModel feature = features.get(position);
        holder.tvGroupTitle.setText(feature.getTitle() != null ? feature.getTitle() : "Feature");

        // Teacher controls
        if (isTeacher) {
            if ("cmcs".equals(feature.getFeatureId())) {
                holder.btnDeleteGroup.setVisibility(View.GONE);
            } else {
                holder.btnDeleteGroup.setVisibility(View.VISIBLE);
                holder.btnDeleteGroup.setOnClickListener(v -> confirmDeleteGroup(feature));
            }
        } else {
            holder.btnDeleteGroup.setVisibility(View.GONE);
        }

        // Sort slides by timestamp
        List<FeatureSlideModel> slideList = new ArrayList<>();
        if (feature.getSlides() != null) {
            for (Map.Entry<String, FeatureSlideModel> entry : feature.getSlides().entrySet()) {
                FeatureSlideModel s = entry.getValue();
                s.setSlideId(entry.getKey());
                slideList.add(s);
            }
            Collections.sort(slideList, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
        }

        // Adapter setup
        FeatureSlideAdapter slideAdapter = new FeatureSlideAdapter(ctx, slideList, isTeacher, feature.getFeatureId(), onDataChangedRunnable);
        holder.vpSlides.setAdapter(slideAdapter);
        holder.ciIndicator.setViewPager(holder.vpSlides);
        slideAdapter.registerAdapterDataObserver(holder.ciIndicator.getAdapterDataObserver());

        // Manage auto scroll
        if (slideList.isEmpty()) {
            holder.stopAutoScroll();
            // Optional: You could show a placeholder here if you added a tvNoSlides to item_feature.xml
        } else {
            holder.startAutoScroll();
        }
    }

    @Override
    public int getItemCount() {
        return features.size();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull FeatureVH holder) {
        super.onViewDetachedFromWindow(holder);
        holder.stopAutoScroll();
    }

    private void confirmDeleteGroup(FeatureModel feature) {
        new AlertDialog.Builder(ctx)
                .setTitle("Delete Feature Section")
                .setMessage("Delete this feature and all its slides?")
                .setPositiveButton("Delete", (dialog, which) -> deleteFeature(feature))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFeature(FeatureModel feature) {
        if ("cmcs".equals(feature.getFeatureId())) {
            return; // Safety
        }
        FirebaseDatabase.getInstance().getReference("features").child(feature.getFeatureId())
                .removeValue()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(ctx, "Feature deleted", Toast.LENGTH_SHORT).show();
                    if (onDataChangedRunnable != null) {
                        onDataChangedRunnable.run();
                    }
                });
    }

    static class FeatureVH extends RecyclerView.ViewHolder {

        TextView tvGroupTitle;
        ImageButton btnDeleteGroup;
        ViewPager2 vpSlides;
        CircleIndicator3 ciIndicator;

        private final Handler autoScrollHandler = new Handler(Looper.getMainLooper());
        private Runnable autoScrollRunnable;

        FeatureVH(@NonNull View v) {
            super(v);
            tvGroupTitle = v.findViewById(R.id.tvGroupTitle);
            btnDeleteGroup = v.findViewById(R.id.btnDeleteGroup);
            vpSlides = v.findViewById(R.id.vpGroupSlides);
            ciIndicator = v.findViewById(R.id.ciGroupIndicator);

            // Disable nested scrolling to prevent conflict with RecyclerView vertical scrolling
            vpSlides.setNestedScrollingEnabled(false);

            vpSlides.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageScrollStateChanged(int state) {
                    super.onPageScrollStateChanged(state);
                    if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                        stopAutoScroll();
                    } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        startAutoScroll();
                    }
                }
            });
        }

        void startAutoScroll() {
            stopAutoScroll();
            if (vpSlides.getAdapter() == null || vpSlides.getAdapter().getItemCount() <= 1) {
                return;
            }

            autoScrollRunnable = new Runnable() {
                @Override
                public void run() {
                    if (vpSlides.getAdapter() == null) {
                        return;
                    }
                    int count = vpSlides.getAdapter().getItemCount();
                    if (count > 1) {
                        int next = (vpSlides.getCurrentItem() + 1) % count;
                        vpSlides.setCurrentItem(next, true);
                        autoScrollHandler.postDelayed(this, AUTO_SCROLL_MS);
                    }
                }
            };
            autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_MS);
        }

        void stopAutoScroll() {
            if (autoScrollRunnable != null) {
                autoScrollHandler.removeCallbacks(autoScrollRunnable);
                autoScrollRunnable = null;
            }
        }
    }
}
