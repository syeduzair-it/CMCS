package com.example.cmcs.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cmcs.AddFeatureSlideActivity;
import com.example.cmcs.R;
import com.example.cmcs.models.FeatureSlideModel;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

public class FeatureSlideAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SLIDE = 0;
    private static final int TYPE_ADD = 1;

    private final Context ctx;
    private final List<FeatureSlideModel> slides;
    private final boolean isTeacher;
    private final String featureId;
    private final Runnable onDataChangedRunnable;

    public FeatureSlideAdapter(Context ctx, List<FeatureSlideModel> slides, boolean isTeacher, String featureId, Runnable onDataChangedRunnable) {
        this.ctx = ctx;
        this.slides = slides;
        this.isTeacher = isTeacher;
        this.featureId = featureId;
        this.onDataChangedRunnable = onDataChangedRunnable;
    }

    @Override
    public int getItemViewType(int position) {
        if (position < slides.size()) {
            return TYPE_SLIDE;
        }
        return TYPE_ADD;
    }

    @Override
    public int getItemCount() {
        int count = slides.size();
        if (isTeacher && count < 8) {
            count++; // Placeholder for Add Slide
        }
        return count;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(ctx);
        if (viewType == TYPE_ADD) {
            View v = inf.inflate(R.layout.item_add_feature_slide, parent, false);
            return new AddVH(v);
        }
        View v = inf.inflate(R.layout.item_feature_slide, parent, false);
        return new SlideVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_ADD) {
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ctx, AddFeatureSlideActivity.class);
                intent.putExtra("featureId", featureId);
                ctx.startActivity(intent);
            });
            return;
        }

        SlideVH h = (SlideVH) holder;
        FeatureSlideModel slide = slides.get(position);

        h.tvTitle.setText(slide.getTitle());
        h.tvDesc.setText(slide.getDescription());

        Glide.with(ctx)
                .load(slide.getImageUrl())
                .centerCrop()
                .placeholder(R.color.surfaceElevated)
                .into(h.ivImage);

        if (isTeacher) {
            h.itemView.setOnLongClickListener(v -> {
                confirmDelete(slide);
                return true;
            });
        }
    }

    private void confirmDelete(FeatureSlideModel slide) {
        new AlertDialog.Builder(ctx)
                .setTitle("Delete Slide")
                .setMessage("Delete this slide?")
                .setPositiveButton("Delete", (dialog, which) -> deleteSlide(slide))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSlide(FeatureSlideModel slide) {
        FirebaseDatabase.getInstance().getReference("features")
                .child(featureId)
                .child("slides")
                .child(slide.getSlideId())
                .removeValue()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(ctx, "Slide deleted", Toast.LENGTH_SHORT).show();
                    if (onDataChangedRunnable != null) {
                        onDataChangedRunnable.run();
                    }
                });
    }

    static class SlideVH extends RecyclerView.ViewHolder {

        ImageView ivImage;
        TextView tvTitle, tvDesc;

        SlideVH(@NonNull View v) {
            super(v);
            ivImage = v.findViewById(R.id.ivFeatureImage);
            tvTitle = v.findViewById(R.id.tvFeatureTitle);
            tvDesc = v.findViewById(R.id.tvFeatureDescription);
        }
    }

    static class AddVH extends RecyclerView.ViewHolder {

        AddVH(@NonNull View v) {
            super(v);
        }
    }
}
