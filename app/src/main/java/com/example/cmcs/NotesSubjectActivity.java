package com.example.cmcs;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.adapters.SubjectAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * NotesSubjectActivity
 *
 * Teacher: sees all subjects + FAB to add a new subject for this
 * (dept/course/year). Student: same list but no FAB — read-only.
 *
 * DB path: notes/<dept>/<course>/<year>/<subject_name> (subject is just a node
 * key)
 */
public class NotesSubjectActivity extends AppCompatActivity {

    private ProgressBar loading;
    private TextView tvEmpty;
    private RecyclerView rv;
    private FloatingActionButton fab;

    private String dept, course, year, role, uid;
    private DatabaseReference subjectsRef;

    private final List<String> subjects = new ArrayList<>();
    private SubjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes_subject);

        Intent i = getIntent();
        dept = i.getStringExtra(NotesClassActivity.EXTRA_DEPT);
        course = i.getStringExtra(NotesClassActivity.EXTRA_COURSE);
        year = i.getStringExtra(NotesClassActivity.EXTRA_YEAR);
        role = i.getStringExtra(NotesClassActivity.EXTRA_ROLE);
        uid = i.getStringExtra("uid");

        loading = findViewById(R.id.ns_loading);
        tvEmpty = findViewById(R.id.ns_empty);
        rv = findViewById(R.id.rv_subjects);
        fab = findViewById(R.id.fab_add_subject);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectAdapter(subjects, subject -> openNotesList(subject));
        rv.setAdapter(adapter);

        MaterialToolbar toolbar = findViewById(R.id.ns_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(course + " — Year " + year);

        subjectsRef = FirebaseDatabase.getInstance()
                .getReference("notes")
                .child(NotesClassActivity.sanitize(dept))
                .child(NotesClassActivity.sanitize(course))
                .child(NotesClassActivity.sanitize(year));

        if ("teacher".equals(role)) {
            fab.setVisibility(View.VISIBLE);
            fab.setOnClickListener(v -> showAddSubjectDialog());
        }

        loadSubjects();
    }

    private void loadSubjects() {
        loading.setVisibility(View.VISIBLE);
        subjectsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                loading.setVisibility(View.GONE);
                subjects.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (child.getKey() != null) {
                        subjects.add(child.getKey());
                    }
                }
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(subjects.isEmpty() ? View.VISIBLE : View.GONE);
                rv.setVisibility(subjects.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loading.setVisibility(View.GONE);
                Toast.makeText(NotesSubjectActivity.this,
                        "Failed to load subjects", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddSubjectDialog() {
        EditText et = new EditText(this);
        et.setHint("Subject name");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int p = Math.round(20 * getResources().getDisplayMetrics().density);
        et.setPadding(p, p, p, p);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Add Subject")
                .setView(et)
                .setPositiveButton("Add", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Prevent duplicate: check if key already exists
                    String key = NotesClassActivity.sanitize(name);
                    if (subjects.contains(key) || subjects.contains(name)) {
                        Toast.makeText(this, "Subject already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Create an empty node (add placeholder to avoid empty-key deletion)
                    subjectsRef.child(key).child("_created").setValue(true)
                            .addOnFailureListener(e
                                    -> Toast.makeText(this, "Failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openNotesList(String subject) {
        Intent i = new Intent(this, NotesListActivity.class);
        i.putExtra(NotesClassActivity.EXTRA_DEPT, dept);
        i.putExtra(NotesClassActivity.EXTRA_COURSE, course);
        i.putExtra(NotesClassActivity.EXTRA_YEAR, year);
        i.putExtra("subject", subject);
        i.putExtra(NotesClassActivity.EXTRA_ROLE, role);
        i.putExtra("uid", uid);
        startActivity(i);
    }
}
