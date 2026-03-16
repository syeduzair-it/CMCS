package com.example.cmcs.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.R;
import com.example.cmcs.models.NoteModel;
import com.google.android.material.button.MaterialButton;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for notes list in NotesListActivity.
 *
 * Delete button is shown only when currentUid matches note's uploadedByUid.
 * Unread dot is shown for notes not yet viewed by the student.
 */
public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.VH> {

    public interface OnNoteAction {
        void onView(NoteModel note);
        void onDelete(NoteModel note);
        /** Called when teacher taps "Viewers" — no-op for students. */
        default void onViewers(NoteModel note) {}
    }

    private final List<NoteModel> notes;
    private final String currentUid;
    private final String role;          // "teacher" | "student"
    private final OnNoteAction listener;
    private final Set<String> unreadNoteIds = new HashSet<>();

    public NoteAdapter(List<NoteModel> notes, String currentUid,
                       String role, OnNoteAction listener) {
        this.notes = notes;
        this.currentUid = currentUid;
        this.role = role;
        this.listener = listener;
    }

    /** Update which notes are unread and refresh. */
    public void setUnreadNoteIds(Set<String> unread) {
        unreadNoteIds.clear();
        if (unread != null) unreadNoteIds.addAll(unread);
        notifyDataSetChanged();
    }

    /** Mark a single note as read and refresh its row. */
    public void markRead(String noteId) {
        if (unreadNoteIds.remove(noteId)) {
            for (int i = 0; i < notes.size(); i++) {
                if (noteId.equals(notes.get(i).getNoteId())) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        NoteModel note = notes.get(position);

        // Type icon
        String icon;
        switch (note.getFileType() != null ? note.getFileType() : "") {
            case "pdf":   icon = "📄"; break;
            case "video": icon = "🎬"; break;
            case "image": icon = "🖼️"; break;
            default:      icon = "📎"; break;
        }
        holder.tvTypeIcon.setText(icon);
        holder.tvTitle.setText(note.getTitle());
        holder.tvUploader.setText("By " + note.getUploadedByName());

        // Unread badge dot (students only)
        holder.badgeDot.setVisibility(
                unreadNoteIds.contains(note.getNoteId()) ? View.VISIBLE : View.GONE);

        // View button
        holder.btnView.setOnClickListener(v -> listener.onView(note));

        // Viewers button — teachers only
        boolean isTeacher = "teacher".equals(role);
        holder.btnViewers.setVisibility(isTeacher ? View.VISIBLE : View.GONE);
        if (isTeacher) {
            holder.btnViewers.setOnClickListener(v -> listener.onViewers(note));
        }

        // Delete — only for the uploader
        boolean isOwner = currentUid != null && currentUid.equals(note.getUploadedByUid());
        holder.btnDelete.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        if (isOwner) {
            holder.btnDelete.setOnClickListener(v -> listener.onDelete(note));
        }
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvTypeIcon, tvTitle, tvUploader;
        MaterialButton btnView, btnViewers, btnDelete;
        View badgeDot;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTypeIcon  = itemView.findViewById(R.id.tv_note_type_icon);
            tvTitle     = itemView.findViewById(R.id.tv_note_title);
            tvUploader  = itemView.findViewById(R.id.tv_note_uploader);
            btnView     = itemView.findViewById(R.id.btn_view_note);
            btnViewers  = itemView.findViewById(R.id.btn_viewers_note);
            btnDelete   = itemView.findViewById(R.id.btn_delete_note);
            badgeDot    = itemView.findViewById(R.id.view_note_badge);
        }
    }
}
