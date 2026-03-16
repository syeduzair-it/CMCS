package com.example.cmcs;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.adapters.NoteAdapter;
import com.example.cmcs.models.NoteModel;
import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * NotesListActivity
 *
 * Lists notes under: notes/<dept>/<course>/<year>/<subject>/<noteId>
 * Sorted DESC by timestamp.
 *
 * Teacher (own notes): View + Delete. Student: View only.
 */
public class NotesListActivity extends AppCompatActivity {

    private ProgressBar loading;
    private TextView tvEmpty;
    private RecyclerView rv;
    private FloatingActionButton fab;

    private String dept, course, year, subject, role, uid;
    private DatabaseReference notesRef;

    private final List<NoteModel> notes = new ArrayList<>();
    private NoteAdapter adapter;
    private ValueEventListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes_list);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        Intent i = getIntent();
        dept = i.getStringExtra(NotesClassActivity.EXTRA_DEPT);
        course = i.getStringExtra(NotesClassActivity.EXTRA_COURSE);
        year = i.getStringExtra(NotesClassActivity.EXTRA_YEAR);
        subject = i.getStringExtra("subject");
        role = i.getStringExtra(NotesClassActivity.EXTRA_ROLE);
        uid = i.getStringExtra("uid");

        loading = findViewById(R.id.nl_loading);
        tvEmpty = findViewById(R.id.nl_empty);
        rv = findViewById(R.id.rv_notes);
        fab = findViewById(R.id.fab_upload_note);

        MaterialToolbar toolbar = findViewById(R.id.nl_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(subject != null
                ? NotesClassActivity.unsanitize(subject) : "Notes");

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NoteAdapter(notes, uid, role, new NoteAdapter.OnNoteAction() {
            @Override
            public void onView(NoteModel note) {
                viewNote(note);
            }

            @Override
            public void onDelete(NoteModel note) {
                confirmDelete(note);
            }

            @Override
            public void onViewers(NoteModel note) {
                openViewers(note);
            }
        });
        rv.setAdapter(adapter);

        notesRef = FirebaseDatabase.getInstance()
                .getReference("notes")
                .child(NotesClassActivity.sanitize(dept))
                .child(NotesClassActivity.sanitize(course))
                .child(NotesClassActivity.sanitize(year))
                .child(subject != null ? subject : "_");

        if ("teacher".equals(role)) {
            fab.setVisibility(View.VISIBLE);
            fab.setOnClickListener(v -> openAddNote());
        }

        loadNotes();
    }

    private void loadNotes() {
        loading.setVisibility(View.VISIBLE);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                loading.setVisibility(View.GONE);
                notes.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    if ("_created".equals(child.getKey())) continue;
                    NoteModel note = child.getValue(NoteModel.class);
                    if (note != null) {
                        note.setNoteId(child.getKey());
                        notes.add(note);
                    }
                }
                Collections.sort(notes,
                        (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                adapter.notifyDataSetChanged();
                boolean empty = notes.isEmpty();
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                rv.setVisibility(empty ? View.GONE : View.VISIBLE);

                // After notes load, compute unread set for students
                if ("student".equals(role) && uid != null) {
                    loadUnreadSet();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loading.setVisibility(View.GONE);
                Toast.makeText(NotesListActivity.this,
                        "Failed to load notes", Toast.LENGTH_SHORT).show();
            }
        };
        notesRef.addValueEventListener(listener);
    }

    // ── Unread tracking ───────────────────────────────────────────────────

    // Per-note view listeners so we react in real time when a note is marked read
    private final List<DatabaseReference> viewRefs = new ArrayList<>();
    private final List<ValueEventListener> viewListeners = new ArrayList<>();
    // Local unread set — drives adapter dots
    private final Set<String> unreadIds = new HashSet<>();

    /**
     * Attaches a real-time listener on noteViews/{noteId}/{uid} for each note.
     * Starts unread, then clears the dot the moment the view entry appears.
     * Only runs for students.
     */
    private void loadUnreadSet() {
        // Detach any previous listeners (e.g. after notes list refreshes)
        for (int i = 0; i < viewRefs.size(); i++) {
            viewRefs.get(i).removeEventListener(viewListeners.get(i));
        }
        viewRefs.clear();
        viewListeners.clear();
        unreadIds.clear();

        for (NoteModel note : notes) {
            String noteId = note.getNoteId();
            if (noteId == null) continue;
            // Assume unread until Firebase says otherwise
            unreadIds.add(noteId);

            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("noteViews").child(noteId).child(uid);
            ValueEventListener vl = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // Viewed — remove from unread set and refresh that row
                        unreadIds.remove(noteId);
                        adapter.markRead(noteId);
                    } else {
                        // Not yet viewed — ensure it's in the unread set
                        unreadIds.add(noteId);
                        adapter.setUnreadNoteIds(new HashSet<>(unreadIds));
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            };
            viewRefs.add(ref);
            viewListeners.add(vl);
            ref.addValueEventListener(vl);
        }
        // Push initial state to adapter
        adapter.setUnreadNoteIds(new HashSet<>(unreadIds));
    }

    private void viewNote(NoteModel note) {
        if (note.getFileUrl() == null || note.getFileUrl().isEmpty()) {
            Toast.makeText(this, "No file attached", Toast.LENGTH_SHORT).show();
            return;
        }
        // Mark as viewed in Firebase (students only, write-once guard)
        if ("student".equals(role) && uid != null && note.getNoteId() != null) {
            DatabaseReference viewRef = FirebaseDatabase.getInstance()
                    .getReference("noteViews")
                    .child(note.getNoteId())
                    .child(uid);
            // Check before writing to avoid duplicate writes
            viewRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) {
                        viewRef.setValue(true);
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
            // Update adapter immediately (optimistic) so dot disappears without waiting
            adapter.markRead(note.getNoteId());
        }
        try {
            String mimeType;
            String fileType = note.getFileType();
            if (fileType == null) fileType = "";
            switch (fileType.toLowerCase()) {
                case "pdf":   mimeType = "application/pdf"; break;
                case "image": mimeType = "image/*"; break;
                case "video": mimeType = "video/*"; break;
                default:      mimeType = "*/*"; break;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.parse(note.getFileUrl()), mimeType);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open file: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(NoteModel note) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Note")
                .setMessage("Delete \"" + note.getTitle() + "\"?")
                .setPositiveButton("Delete", (d, w) -> deleteNote(note))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteNote(NoteModel note) {
        // Remove from DB
        notesRef.child(note.getNoteId()).removeValue()
                .addOnFailureListener(e
                        -> Toast.makeText(this, "Delete failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());

        // Note: Cloudinary assets cannot be deleted from the client without
        // an API secret. DB record is removed above; Cloudinary URL is orphaned.
    }

    private void openViewers(NoteModel note) {
        Intent i = new Intent(this, NoteViewersActivity.class);
        i.putExtra(NoteViewersActivity.EXTRA_NOTE_ID, note.getNoteId());
        i.putExtra(NoteViewersActivity.EXTRA_NOTE_TITLE, note.getTitle());
        i.putExtra(NoteViewersActivity.EXTRA_DEPT, dept);
        i.putExtra(NoteViewersActivity.EXTRA_COURSE, course);
        i.putExtra(NoteViewersActivity.EXTRA_YEAR, year);
        startActivity(i);
    }

    private void openAddNote() {
        Intent i = new Intent(this, AddNoteActivity.class);
        i.putExtra(NotesClassActivity.EXTRA_DEPT, dept);
        i.putExtra(NotesClassActivity.EXTRA_COURSE, course);
        i.putExtra(NotesClassActivity.EXTRA_YEAR, year);
        i.putExtra("subject", subject);
        i.putExtra("uid", uid);
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) {
            notesRef.removeEventListener(listener);
        }
        // Detach per-note view listeners
        for (int i = 0; i < viewRefs.size(); i++) {
            viewRefs.get(i).removeEventListener(viewListeners.get(i));
        }
        viewRefs.clear();
        viewListeners.clear();
    }
}
