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
    
    // ── Group creation ────────────────────────────────────────────────────
    private boolean isGroupSelectionMode = false;
    private final List<UserModel> selectedUsers = new ArrayList<>();

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
        adapter.setOnUserCheckListener(this::onUserChecked);

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
        fabCreateGroup.setOnClickListener(v -> {
            if (isGroupSelectionMode) {
                // Confirm group creation
                createGroupFromSelection();
            } else {
                // Enter group selection mode
                enterGroupSelectionMode();
            }
        });

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
    // Group chat creation
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Enter group selection mode - show checkboxes and filter eligible users
     */
    private void enterGroupSelectionMode() {
        isGroupSelectionMode = true;
        selectedUsers.clear();
        
        // Update FAB icon
        fabCreateGroup.setImageResource(R.drawable.ic_check);
        
        // Filter users to show only eligible students (same course, year, gender)
        List<UserModel> eligibleUsers = getEligibleGroupMembers();
        
        if (eligibleUsers.isEmpty()) {
            Toast.makeText(this, "No eligible students found for group creation", 
                    Toast.LENGTH_SHORT).show();
            exitGroupSelectionMode();
            return;
        }
        
        // Enable selection mode in adapter
        adapter.setSelectionMode(true);
        adapter.updateData(eligibleUsers);
        
        Toast.makeText(this, "Select at least 2 students to create a group", 
                Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Exit group selection mode
     */
    private void exitGroupSelectionMode() {
        isGroupSelectionMode = false;
        selectedUsers.clear();
        
        // Restore FAB icon
        fabCreateGroup.setImageResource(R.drawable.ic_group_add);
        
        // Disable selection mode in adapter
        adapter.setSelectionMode(false);
        
        // Restore original user list
        filterAndDisplayUsers();
    }
    
    /**
     * Get eligible users for group creation (same course, year, gender)
     */
    private List<UserModel> getEligibleGroupMembers() {
        List<UserModel> eligible = new ArrayList<>();
        
        for (UserModel u : cachedUsers) {
            if (myUid.equals(u.getAuthUid())) {
                continue; // Skip self
            }
            
            // Only students with same course, year, and gender
            if ("student".equalsIgnoreCase(normalize(u.getRole()))
                    && normalize(u.getCourse()).equals(normalize(myProfile.getCourse()))
                    && normalize(u.getYear()).equals(normalize(myProfile.getYear()))
                    && normalize(u.getGender()).equals(normalize(myProfile.getGender()))) {
                eligible.add(u);
            }
        }
        
        return eligible;
    }
    
    /**
     * Handle user checkbox toggle
     */
    private void onUserChecked(UserModel user, boolean isChecked) {
        if (isChecked) {
            if (!selectedUsers.contains(user)) {
                selectedUsers.add(user);
            }
        } else {
            selectedUsers.remove(user);
        }
        
        // Update FAB text to show count
        int totalMembers = selectedUsers.size() + 1; // +1 for current user
        fabCreateGroup.setContentDescription("Create group with " + totalMembers + " members");
    }
    
    /**
     * Create group from selected users
     */
    private void createGroupFromSelection() {
        // Validate minimum group size (current user + 2 others = 3 total)
        if (selectedUsers.size() < 2) {
            Toast.makeText(this, "Group must contain at least 3 members", 
                    Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show group name dialog
        EditText nameInput = new EditText(this);
        nameInput.setHint("Group name");
        nameInput.setSingleLine(true);
        
        new AlertDialog.Builder(this)
                .setTitle("Create Group")
                .setMessage("Enter a name for your group (" + (selectedUsers.size() + 1) + " members)")
                .setView(nameInput)
                .setPositiveButton("Create", (dialog, which) -> {
                    String groupName = nameInput.getText().toString().trim();
                    if (groupName.isEmpty()) {
                        Toast.makeText(this, "Please enter a group name", 
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createGroup(groupName);
                })
                .setNegativeButton("Cancel", (dialog, which) -> exitGroupSelectionMode())
                .setOnCancelListener(dialog -> exitGroupSelectionMode())
                .show();
    }
    
    /**
     * Create group chat with selected members
     */
    private void createGroup(String groupName) {
        // Build participant list (current user + selected users)
        List<String> participantUids = new ArrayList<>();
        participantUids.add(myUid);
        for (UserModel user : selectedUsers) {
            participantUids.add(user.getAuthUid());
        }
        
        // Sort UIDs to create deterministic group ID
        java.util.Collections.sort(participantUids);
        String sortedParticipants = android.text.TextUtils.join("_", participantUids);
        
        // Check if group with same participants already exists
        checkAndCreateGroup(groupName, participantUids, sortedParticipants);
    }
    
    /**
     * Check for duplicate group and create if not exists
     */
    private void checkAndCreateGroup(String groupName, List<String> participantUids, 
                                      String sortedParticipants) {
        chatsRef.orderByChild("type").equalTo("group")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        // Check each group for matching participants
                        for (DataSnapshot groupSnapshot : snapshot.getChildren()) {
                            DataSnapshot participantsSnapshot = groupSnapshot.child("participants");
                            
                            // Build sorted participant list from this group
                            List<String> groupParticipants = new ArrayList<>();
                            for (DataSnapshot p : participantsSnapshot.getChildren()) {
                                groupParticipants.add(p.getKey());
                            }
                            java.util.Collections.sort(groupParticipants);
                            String groupSortedParticipants = android.text.TextUtils.join("_", 
                                    groupParticipants);
                            
                            // If participants match, open existing group
                            if (groupSortedParticipants.equals(sortedParticipants)) {
                                String existingGroupId = groupSnapshot.getKey();
                                String existingGroupName = groupSnapshot.child("groupName")
                                        .getValue(String.class);
                                
                                Toast.makeText(SelectUserActivity.this,
                                        "Group \"" + existingGroupName + "\" already exists",
                                        Toast.LENGTH_SHORT).show();
                                
                                openGroupChat(existingGroupId, existingGroupName);
                                return;
                            }
                        }
                        
                        // No duplicate found, create new group
                        createNewGroup(groupName, participantUids);
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(SelectUserActivity.this,
                                "Error checking groups: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    /**
     * Create new group in Firebase
     */
    private void createNewGroup(String groupName, List<String> participantUids) {
        String groupId = chatsRef.push().getKey();
        if (groupId == null) {
            Toast.makeText(this, "Failed to generate group ID", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Build participants map
        Map<String, Object> participants = new HashMap<>();
        for (String uid : participantUids) {
            participants.put(uid, true);
        }
        
        // Build group data
        Map<String, Object> groupData = new HashMap<>();
        groupData.put("type", "group");
        groupData.put("name", groupName); // Changed from "groupName" to "name" to match ChatModel
        groupData.put("createdBy", myUid);
        groupData.put("participants", participants);
        groupData.put("lastMessage", "");
        groupData.put("lastTimestamp", ServerValue.TIMESTAMP);
        
        // Build multi-path update
        Map<String, Object> updates = new HashMap<>();
        updates.put("chats/" + groupId, groupData);
        
        // Add userChats entry for each participant
        for (String uid : participantUids) {
            updates.put("userChats/" + uid + "/" + groupId + "/visible", true);
            updates.put("userChats/" + uid + "/" + groupId + "/unreadCount", 0);
        }
        
        // Execute update
        FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Group \"" + groupName + "\" created!", 
                            Toast.LENGTH_SHORT).show();
                    openGroupChat(groupId, groupName);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to create group: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    exitGroupSelectionMode();
                });
    }
    
    /**
     * Open group chat activity
     */
    private void openGroupChat(String groupId, String groupName) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_ID, groupId);
        intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, groupName);
        intent.putExtra(ChatActivity.EXTRA_OTHER_UID, groupId); // Use groupId as placeholder
        intent.putExtra("isGroup", true);
        startActivity(intent);
        finish();
    }
    
    /**
     * Old group creation dialog - kept for reference, will be removed
     */
    private void showCreateGroupDialog() {
        // This method is replaced by enterGroupSelectionMode()
        enterGroupSelectionMode();
    }

    /**
     * Group creation skeleton - replaced by new implementation
     */
    private void createGroupChat(String groupName, boolean isTeacher) {
        // This method is replaced by createGroup()
    }

}
