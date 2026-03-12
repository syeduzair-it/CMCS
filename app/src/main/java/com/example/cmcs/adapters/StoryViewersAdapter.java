package com.example.cmcs.adapters;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import com.example.cmcs.R;
import com.example.cmcs.models.ViewerModel;

import java.util.List;

public class StoryViewersAdapter extends RecyclerView.Adapter<StoryViewersAdapter.VH> {

    private final Context ctx;
    private final List<ViewerModel> list;

    public StoryViewersAdapter(Context ctx, List<ViewerModel> list) {
        this.ctx = ctx;
        this.list = list;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_viewer, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ViewerModel model = list.get(position);
        holder.tvName.setText(model.getName() != null ? model.getName() : "Unknown Student");

        Glide.with(ctx)
                .load(model.getProfileImage())
                .placeholder(R.drawable.ic_user)
                .circleCrop()
                .into(holder.ivImage);

        if (model.getTimestamp() > 0) {
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    model.getTimestamp(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            holder.tvTime.setText(relativeTime);
        } else {
            holder.tvTime.setText("Just now");
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvName, tvTime;
        android.widget.ImageView ivImage;

        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvViewerName);
            tvTime = v.findViewById(R.id.tvViewerTime);
            ivImage = v.findViewById(R.id.ivViewerImage);
        }
    }
}
