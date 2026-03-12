package com.example.cmcs.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.R;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class ClassSelectionAdapter extends RecyclerView.Adapter<ClassSelectionAdapter.ClassViewHolder> {

    private final Context context;
    private final List<String> classList;
    private final OnClassSelectedListener listener;

    public interface OnClassSelectedListener {

        void onClassSelected(String className);
    }

    public ClassSelectionAdapter(Context context, List<String> classList, OnClassSelectedListener listener) {
        this.context = context;
        this.classList = classList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ClassViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_class_selection, parent, false);
        return new ClassViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClassViewHolder holder, int position) {
        String className = classList.get(position);
        holder.tvClassName.setText(className);

        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClassSelected(className);
            }
        });
    }

    @Override
    public int getItemCount() {
        return classList != null ? classList.size() : 0;
    }

    public static class ClassViewHolder extends RecyclerView.ViewHolder {

        MaterialCardView cardView;
        TextView tvClassName;

        public ClassViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            tvClassName = itemView.findViewById(R.id.tvClassName);
        }
    }
}
