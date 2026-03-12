package com.example.cmcs.welcome;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.cmcs.R;

import java.util.List;

public class WelcomeSliderAdapter extends RecyclerView.Adapter<WelcomeSliderAdapter.SlideViewHolder> {

    List<SlideItem> slideItems;

    public WelcomeSliderAdapter(List<SlideItem> slideItems) {
        this.slideItems = slideItems;
    }

    @NonNull
    @Override
    public SlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.welcome_slide_item, parent, false);
        return new SlideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideViewHolder holder, int position) {
        SlideItem item = slideItems.get(position);
        holder.imageSlide.setImageResource(item.getImageResId());
        holder.titleSlide.setText(item.getTitle());
        holder.descSlide.setText(item.getDescription());
    }

    @Override
    public int getItemCount() {
        return slideItems.size();
    }

    public class SlideViewHolder extends RecyclerView.ViewHolder {
        ImageView imageSlide;
        TextView titleSlide, descSlide;

        public SlideViewHolder(@NonNull View itemView) {
            super(itemView);
            imageSlide = itemView.findViewById(R.id.imageSlide);
            titleSlide = itemView.findViewById(R.id.titleSlide);
            descSlide = itemView.findViewById(R.id.descSlide);
        }
    }
}
