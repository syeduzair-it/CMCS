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
 * Adapter for subject list in NotesSubjectActivity.
 */
public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.VH> {

    public interface OnSubjectClick {

        void onSubjectClick(String subject);
    }

    private final List<String> subjects;
    private final OnSubjectClick listener;

    public SubjectAdapter(List<String> subjects, OnSubjectClick listener) {
        this.subjects = subjects;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String subject = subjects.get(position);
        holder.tvName.setText(subject);
        holder.itemView.setOnClickListener(v -> listener.onSubjectClick(subject));
    }

    @Override
    public int getItemCount() {
        return subjects.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvName;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_subject_name);
        }
    }
}
