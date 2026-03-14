package com.example.cmcs;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.adapters.ClassSelectionAdapter;
import com.example.cmcs.adapters.UserSelectionAdapter;
import com.example.cmcs.models.UserModel;
import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SelectUserActivity — "Start Chat" screen.
 *
 * Opened from the FAB in ChatsFragment. Authentication authority: FirebaseAuth
 * only. Identity source: users/<authUid> ONLY (never students/ or teachers/).
 *
 * Role-based visibility rules:
 * ┌─────────────┬────────────────────────────────────────────────────────┐ │ My
 * role │ Who I can see │
 * ├─────────────┼────────────────────────────────────────────────────────┤ │
 * student │ Students: same dept + course + year + gender │ │ │ Teachers: ALL
 * teachers (any dept) │
 * ├─────────────┼────────────────────────────────────────────────────────┤ │
 * teacher │ Students: same department (all courses/years/genders) │ │ │
 * Teachers: ALL teachers │
 * └─────────────┴────────────────────────────────────────────────────────┘
 *
 * Private chat: uses deterministic chatId = sort(uid1, uid2).join("_") → O(1)
 * lookup, no duplicate chats.
 *
 * Group chat: skeleton (dialog + Firebase write + Toast).
 */
public class SelectUserActivity extends AppCompatActivity {

    // ── Firebase ──────────────────────────────────────────────────────────
    private DatabaseReference usersRef;
    private DatabaseReference chatsRef;

    // ── My identity ───────────────────────────────────────────────────────
    private String myUid;
    private UserModel myProfile;           // loaded from users/<myUid>

    // ── UI ────────────────────────────────────────────────────────────────
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private RecyclerView teachersRecyclerView;
    private RecyclerView classesRecyclerView;
    private android.widget.TextView tvClassesHeader;
    private TextInputEditText searchInput;
    private FloatingActionButton fabCreateGroup;
    private UserSelectionAdapter adapter;
    private ClassSelectionAdapter classesAdapter;

    // ── Data ──────────────────────────────────────────────────────────────
    public static final List<UserModel> cachedUsers = new ArrayList<>();
    private final List<String> classesList = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_user);

        // Apply edge-to-edge with light status bar (dark icons for white background)
        WindowInsetsHelper.setupEdgeToEdge(this, true);

        // 1. Auth check — FirebaseAuth is the single source of truth
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            finish();
            return;
        }
        myUid = firebaseUser.getUid();

        // 2. Init Firebase references (online-only — no keepSynced here)
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        usersRef = db.getReference("users");
        chatsRef = db.getReference("chats");

        // 3. Bind views
        bindViews();

        // 4. Toolbar back navigation
        MaterialToolbar toolbar = findViewById(R.id.toolbar_select_user);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 5. RecyclerView
        adapter = new UserSelectionAdapter(this, new ArrayList<>());
        teachersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        teachersRecyclerView.setAdapter(adapter);

        adapter.setOnUserClickListener(this::onUserSelected);

        // 6. Search
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int c, int a) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int b, int c) {
                adapter.filter(s.toString());
            }
        });

        // 7. Group FAB
        fabCreateGroup.setOnClickListener(v -> showCreateGroupDialog());

        // 8. Load my profile first, then query allowed users
        loadMyProfile();
    }

    // ─────────────────────────────────────────────────────────────────────
    // View binding
    // ─────────────────────────────────────────────────────────────────────
    private void bindViews() {
        progressBar = findViewById(R.id.progress_bar);
        emptyState = findViewById(R.id.empty_state);
        teachersRecyclerView = findViewById(R.id.teachers_recycler_view);
        classesRecyclerView = findViewById(R.id.classes_recycler_view);
        tvClassesHeader = findViewById(R.id.tv_classes_header);
        searchInput = findViewById(R.id.search_input);
        fabCreateGroup = findViewById(R.id.fab_create_group);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 1: Load my own profile from users/<myUid>
    // ─────────────────────────────────────────────────────────────────────
    private void loadMyProfile() {
        usersRef.child(myUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    myProfile = snapshot.getValue(UserModel.class);
                    if (myProfile != null) {
                        myProfile.setAuthUid(myUid);
                        // Now query allowed users based on my role
                        queryAllowedUsers();
                        return;
                    }
                }

                // Fallback: Check if user is a student in students/ node
                DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("students");
                studentsRef.orderByChild("authUid").equalTo(myUid)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot studentsSnapshot) {
                                if (studentsSnapshot.exists()) {
                                    for (DataSnapshot child : studentsSnapshot.getChildren()) {
                                        myProfile = new UserModel();
                                        myProfile.setAuthUid(child.child("authUid").getValue(String.class));
                                        myProfile.setName(child.child("name").getValue(String.class));
                                        myProfile.setDepartment(child.child("department").getValue(String.class));
                                        myProfile.setCourse(child.child("course").getValue(String.class));
                                        myProfile.setYear(child.child("year").getValue(String.class));
                                        myProfile.setGender(child.child("gender").getValue(String.class));
                                        myProfile.setRole("student");
                                        myProfile.setProfileImage(child.child("profileImage").getValue(String.class));

                                        queryAllowedUsers();
                                        return; // Break after finding the first match
                                    }
                                }

                                Toast.makeText(SelectUserActivity.this,
                                        "Could not load your profile. Please try again.",
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Toast.makeText(SelectUserActivity.this,
                                        "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SelectUserActivity.this,
                        "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────
    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 2: Query allowed users based on role rules
    // ─────────────────────────────────────────────────────────────────────
    private void queryAllowedUsers() {
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        DatabaseReference studentsRef = db.getReference("students");

        // ------------------------------------------------------------------
        // Synchronization: fire BOTH queries in parallel.
        // Only swap tempUsers → cachedUsers and call filterAndDisplayUsers()
        // once BOTH callbacks have returned (counter reaches 0).
        // ------------------------------------------------------------------
        final List<UserModel> tempUsers = new ArrayList<>();
        final Set<String> loadedUids = new HashSet<>();
        final int[] pendingQueries = {2};   // two queries outstanding
        final int[] teachersCount = {0};
        final int[] studentsCount = {0};

        final Runnable onBothFinished = () -> {
            // Atomically swap temp → cache
            cachedUsers.clear();
            cachedUsers.addAll(tempUsers);

            Log.d("UserLoader", "Teachers loaded: " + teachersCount[0]);
            Log.d("UserLoader", "Students loaded: " + studentsCount[0]);
            Log.d("UserLoader", "Total cached users: " + cachedUsers.size());

            filterAndDisplayUsers();
        };

        // ── Query 1: teachers from users/ ─────────────────────────────────
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot usersSnapshot) {
                for (DataSnapshot child : usersSnapshot.getChildren()) {
                    UserModel u = child.getValue(UserModel.class);
                    if (u != null) {
                        String authUid = child.getKey();   // teachers: key IS the authUid
                        u.setAuthUid(authUid);
                        if (authUid != null && !loadedUids.contains(authUid)) {
                            tempUsers.add(u);
                            loadedUids.add(authUid);
                            teachersCount[0]++;
                        }
                    }
                }
                pendingQueries[0]--;
                if (pendingQueries[0] == 0) {
                    onBothFinished.run();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SelectUserActivity.this,
                            "Failed to load users: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });

        // ── Query 2: students from students/ ──────────────────────────────
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot studentsSnapshot) {
                for (DataSnapshot child : studentsSnapshot.getChildren()) {
                    // IMPORTANT: key is a random push-key; real UID is inside "authUid"
                    String authUid = child.child("authUid").getValue(String.class);
                    if (authUid != null && !loadedUids.contains(authUid)) {
                        UserModel u = new UserModel();
                        u.setAuthUid(authUid);
                        u.setName(child.child("name").getValue(String.class));
                        u.setDepartment(child.child("department").getValue(String.class));
                        u.setCourse(child.child("course").getValue(String.class));
                        u.setYear(child.child("year").getValue(String.class));
                        u.setGender(child.child("gender").getValue(String.class));
                        u.setRole("student");
                        u.setProfileImage(child.child("profileImage").getValue(String.class));
                        tempUsers.add(u);
                        loadedUids.add(authUid);
                        studentsCount[0]++;
                    }
                }
                pendingQueries[0]--;
                if (pendingQueries[0] == 0) {
                    onBothFinished.run();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SelectUserActivity.this,
                            "Failed to load students: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void filterAndDisplayUsers() {
        boolean isTeacher = "teacher".equalsIgnoreCase(normalize(myProfile.getRole()));
        List<UserModel> displayTeachersAndPeers = new ArrayList<>();

        // Operate on a local snapshot to prevent ConcurrentModificationException
        List<UserModel> users = new ArrayList<>(cachedUsers);
        for (UserModel u : users) {
            if (myUid.equals(u.getAuthUid())) {
                continue;
            }

            String theirRole = normalize(u.getRole());
            if ("teacher".equals(theirRole)) {
                // Everyone sees teachers
                displayTeachersAndPeers.add(u);
            } else if ("student".equals(theirRole) && !isTeacher) {
                // Student sees same-class students
                if (normalize(u.getDepartment()).contains(normalize(myProfile.getDepartment()))
                        && normalize(u.getCourse()).contains(normalize(myProfile.getCourse()))
                        && normalize(u.getGender()).contains(normalize(myProfile.getGender()))
                        && normalize(u.getYear()).contains(normalize(myProfile.getYear()))) {
                    displayTeachersAndPeers.add(u);
                }
            }
        }

        displayTeachersAndPeers.sort((a, b) -> {
            boolean aT = "teacher".equalsIgnoreCase(normalize(a.getRole()));
            boolean bT = "teacher".equalsIgnoreCase(normalize(b.getRole()));
            if (aT != bT) {
                return aT ? -1 : 1;
            }
            String an = a.getName() != null ? a.getName() : "";
            String bn = b.getName() != null ? b.getName() : "";
            return an.compareToIgnoreCase(bn);
        });

        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            if (displayTeachersAndPeers.isEmpty() && (!isTeacher)) {
                emptyState.setVisibility(View.VISIBLE);
                teachersRecyclerView.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                teachersRecyclerView.setVisibility(View.VISIBLE);
                adapter.updateData(displayTeachersAndPeers);
            }

            if (isTeacher) {
                setupTeacherClasses();
            } else {
                classesRecyclerView.setVisibility(View.GONE);
                tvClassesHeader.setVisibility(View.GONE);
            }
        });
    }

    private void setupTeacherClasses() {
        classesRecyclerView.setVisibility(View.VISIBLE);
        tvClassesHeader.setVisibility(View.VISIBLE);
        classesList.clear();

        String dept = normalize(myProfile.getDepartment());
        if (dept.contains("computer")) {
            classesList.add("BCA 1");
            classesList.add("BCA 2");
            classesList.add("BCA 3");
            classesList.add("BSc 1");
            classesList.add("BSc 2");
            classesList.add("BSc 3");
            classesList.add("MSc 1");
            classesList.add("MSc 2");
        } else if (dept.contains("management")) {
            classesList.add("BBA 1");
            classesList.add("BBA 2");
            classesList.add("BBA 3");
            classesList.add("BCom 1");
            classesList.add("BCom 2");
            classesList.add("BCom 3");
            classesList.add("MBA 1");
            classesList.add("MBA 2");
        }

        classesAdapter = new ClassSelectionAdapter(this, classesList, className -> {
            String[] parts = className.split(" ");
            if (parts.length >= 2) {
                String course = parts[0];
                String year = parts[1];
                Intent intent = new Intent(this, ClassStudentsActivity.class);
                intent.putExtra("department", myProfile.getDepartment());
                intent.putExtra("course", course);
                intent.putExtra("year", year);
                startActivity(intent);
            }
        });
        classesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        classesRecyclerView.setAdapter(classesAdapter);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private chat creation (deterministic chatId)
    // ─────────────────────────────────────────────────────────────────────
    /**
     * Called when the user taps a row in the list.
     *
     * Deterministic chatId: sort the two UIDs lexicographically and join with
     * "_". This guarantees a unique, collision-free ID for any pair — no
     * scanning needed.
     */
    private void onUserSelected(UserModel other) {
        String otherUid = other.getAuthUid();
        if (otherUid == null) {
            return;
        }

        // Build deterministic chatId
        String chatId = myUid.compareTo(otherUid) < 0
                ? myUid + "_" + otherUid
                : otherUid + "_" + myUid;

        // Open chat immediately; it will be created on first message send it ChatActivity
        openChat(chatId, other);
    }

    /**
     * Opens the individual chat screen. Passes chatId, otherUid, name, and
     * profile image to ChatActivity.
     */
    private void openChat(String chatId, UserModel other) {
        startActivity(ChatActivity.newIntent(
                this,
                chatId,
                other.getAuthUid(),
                other.getName(),
                other.getProfileImage()
        ));
        finish();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group chat creation (skeleton)
    // ─────────────────────────────────────────────────────────────────────────
    private void showCreateGroupDialog() {
        boolean isTeacher = "teacher".equalsIgnoreCase(myProfile.getRole());

        // Build dialog with a single EditText for group name
        EditText nameInput = new EditText(this);
        nameInput.setHint("Group name");
        nameInput.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("Create Group")
                .setMessage(isTeacher
                        ? "Create a department group for " + myProfile.getDepartment()
                        : "Create a class group (same course, year & gender)")
                .setView(nameInput)
                .setPositiveButton("Create", (dialog, which) -> {
                    String groupName = nameInput.getText().toString().trim();
                    if (groupName.isEmpty()) {
                        Toast.makeText(this, "Please enter a group name",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createGroupChat(groupName, isTeacher);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Group creation skeleton.
     *
     * Writes the group chat record. Member population (loading allowed users
     * and adding their userChats entries) is left as a TODO for the full group
     * implementation — the structure is correct.
     *
     * Max 50 participants enforced during member-add phase (TODO).
     */
    private void createGroupChat(String groupName, boolean isTeacher) {
        String groupId = chatsRef.push().getKey();
        if (groupId == null) {
            Toast.makeText(this, "Failed to generate group ID", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> participants = new HashMap<>();
        participants.put(myUid, true); // creator is first member

        Map<String, Object> groupData = new HashMap<>();
        groupData.put("type", "group");
        groupData.put("groupName", groupName);
        groupData.put("createdBy", myUid);
        groupData.put("participants", participants);
        groupData.put("lastMessage", "");
        groupData.put("lastTimestamp", ServerValue.TIMESTAMP);

        // Add membership restriction metadata for future enforcement
        if (!isTeacher) {
            groupData.put("restrictDept", myProfile.getDepartment());
            groupData.put("restrictCourse", myProfile.getCourse());
            groupData.put("restrictYear", myProfile.getYear());
            groupData.put("restrictGender", myProfile.getGender());
        } else {
            groupData.put("restrictDept", myProfile.getDepartment());
        }

        Map<String, Object> multiPathUpdate = new HashMap<>();
        multiPathUpdate.put("chats/" + groupId, groupData);
        multiPathUpdate.put("userChats/" + myUid + "/" + groupId + "/visible", true);

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(multiPathUpdate)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this,
                            "Group \"" + groupName + "\" created! Add members next.",
                            Toast.LENGTH_LONG).show();
                    // TODO: startActivity(new Intent(this, GroupMembersActivity.class)
                    //           .putExtra("groupId", groupId));
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                "Failed to create group: " + e.getMessage(),
                Toast.LENGTH_SHORT).show());
    }

}
