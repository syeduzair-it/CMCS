package com.example.cmcs.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for subject list in NotesSubjectActivity.
 */
public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.VH> {

    public interface OnSubjectClick {
        void onSubjectClick(String subject);
    }

    private final List<String> subjects;
    private final OnSubjectClick listener;
    private final Set<String> unreadSubjects = new HashSet<>();

    public SubjectAdapter(List<String> subjects, OnSubjectClick listener) {
        this.subjects = subjects;
        this.listener = listener;
    }

    /** Update which subjects have unread notes and refresh the list. */
    public void setUnreadSubjects(Set<String> unread) {
        unreadSubjects.clear();
        if (unread != null) unreadSubjects.addAll(unread);
        notifyDataSetChanged();
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
        holder.badgeDot.setVisibility(
                unreadSubjects.contains(subject) ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onSubjectClick(subject));
    }

    @Override
    public int getItemCount() {
        return subjects.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvName;
        View badgeDot;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_subject_name);
            badgeDot = itemView.findViewById(R.id.view_subject_badge);
        }
    }
}
