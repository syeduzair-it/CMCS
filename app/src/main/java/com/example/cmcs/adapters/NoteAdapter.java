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

import java.util.List;

/**
 * Adapter for notes list in NotesListActivity.
 *
 * Delete button is shown only when currentUid matches note's uploadedByUid.
 */
public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.VH> {

    public interface OnNoteAction {

        void onView(NoteModel note);

        void onDelete(NoteModel note);
    }

    private final List<NoteModel> notes;
    private final String currentUid;
    private final OnNoteAction listener;

    public NoteAdapter(List<NoteModel> notes, String currentUid, OnNoteAction listener) {
        this.notes = notes;
        this.currentUid = currentUid;
        this.listener = listener;
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
            case "pdf":
                icon = "📄";
                break;
            case "video":
                icon = "🎬";
                break;
            case "image":
                icon = "🖼️";
                break;
            default:
                icon = "📎";
                break;
        }
        holder.tvTypeIcon.setText(icon);
        holder.tvTitle.setText(note.getTitle());
        holder.tvUploader.setText("By " + note.getUploadedByName());

        // View button
        holder.btnView.setOnClickListener(v -> listener.onView(note));

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
        MaterialButton btnView, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTypeIcon = itemView.findViewById(R.id.tv_note_type_icon);
            tvTitle = itemView.findViewById(R.id.tv_note_title);
            tvUploader = itemView.findViewById(R.id.tv_note_uploader);
            btnView = itemView.findViewById(R.id.btn_view_note);
            btnDelete = itemView.findViewById(R.id.btn_delete_note);
        }
    }
}
