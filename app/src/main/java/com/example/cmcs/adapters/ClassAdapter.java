package com.example.cmcs.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.R;

import java.util.List;

/**
 * Adapter for the class picker in NotesClassActivity. Each item is
 * "course|year" (joined with pipe to keep them together).
 */
public class ClassAdapter extends RecyclerView.Adapter<ClassAdapter.VH> {

    public interface OnClassClick {

        void onClassClick(String course, String year);
    }

    private final List<String> items; // each = "course|year"
    private final OnClassClick listener;

    public ClassAdapter(List<String> items, OnClassClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_class_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String raw = items.get(position);
        String[] parts = raw.split("\\|", 2);
        String course = parts.length > 0 ? parts[0] : raw;
        String year = parts.length > 1 ? parts[1] : "";

        holder.tvName.setText(course);
        holder.tvYear.setText("Year: " + year);

        holder.itemView.setOnClickListener(v -> listener.onClassClick(course, year));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvName, tvYear;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_class_name);
            tvYear = itemView.findViewById(R.id.tv_class_year);
        }
    }
}
