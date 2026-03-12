package com.example.cmcs.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cmcs.HighlightViewerActivity;
import com.example.cmcs.R;
import com.example.cmcs.models.HighlightModel;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Horizontal RecyclerView adapter for the Highlights strip. Each item is a
 * circular cover image + title label. Tapping opens HighlightViewerActivity.
 */
public class HighlightAdapter extends RecyclerView.Adapter<HighlightAdapter.VH> {

    private final Context ctx;
    private final List<HighlightModel> highlights;
    private final boolean isTeacher;

    public HighlightAdapter(Context ctx, List<HighlightModel> highlights, boolean isTeacher) {
        this.ctx = ctx;
        this.highlights = highlights;
        this.isTeacher = isTeacher;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx)
                .inflate(R.layout.item_highlight, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        HighlightModel hl = highlights.get(position);
        h.tvTitle.setText(hl.getTitle());

        if (hl.getCoverUrl() != null && !hl.getCoverUrl().isEmpty()) {
            Glide.with(ctx)
                    .load(hl.getCoverUrl())
                    .thumbnail(0.3f)
                    .placeholder(R.color.surfaceElevated)
                    .circleCrop()
                    .into(h.ivCover);
        } else {
            Glide.with(ctx)
                    .load(R.drawable.cmcs_logo)
                    .circleCrop()
                    .into(h.ivCover);
        }

        h.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(ctx, HighlightViewerActivity.class);
            intent.putExtra(HighlightViewerActivity.EXTRA_HIGHLIGHT_ID, hl.getHighlightId());
            intent.putExtra(HighlightViewerActivity.EXTRA_HIGHLIGHT_TITLE, hl.getTitle());
            intent.putExtra(HighlightViewerActivity.EXTRA_IS_TEACHER, isTeacher);
            intent.putExtra(HighlightViewerActivity.EXTRA_CREATED_BY_UID, hl.getCreatedByUid());
            ctx.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return highlights.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        CircleImageView ivCover;
        TextView tvTitle;

        VH(@NonNull View v) {
            super(v);
            ivCover = v.findViewById(R.id.ivHighlightCover);
            tvTitle = v.findViewById(R.id.tvHighlightTitle);
        }
    }
}
