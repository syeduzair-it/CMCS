package com.example.cmcs.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseUser;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.cmcs.R;
import com.example.cmcs.SelectUserActivity;
import com.example.cmcs.adapters.ChatListAdapter;
import com.example.cmcs.login.Login;
import com.example.cmcs.models.ChatModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import de.hdodenhof.circleimageview.CircleImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ChatsFragment
 *
 * Hosts a DrawerLayout with: – a MaterialToolbar (hamburger + "CMCS" title) – a
 * RecyclerView showing the user's chats – a FAB to start a new chat – a
 * NavigationView (Profile, Settings, Logout, About CMCS)
 *
 * Data flow: 1. Read authUid from FirebaseAuth.getCurrentUser(). 2. Listen to
 * userChats/<authUid> for chatIds. 3. For each chatId fetch chats/<chatId> for
 * details. 4. For private chats, fetch the other user's profile from
 * users/<uid>. 5. Sort by lastTimestamp DESC and update adapter.
 *
 * Firebase persistence is enabled in CMCSApplication (once per process).
 * keepSynced(true) is set on userChats and chats references here.
 */
public class ChatsFragment extends Fragment {

    // ── Constants ─────────────────────────────────────────────────────────
    private static final String PREFS_NAME = "cmcs_prefs";

    // ── Views ─────────────────────────────────────────────────────────────
    private DrawerLayout drawerLayout;
    private RecyclerView recyclerView;
    private View emptyStateView;
    private NavigationView navigationView;

    // ── Drawer header views (loaded after header inflation) ───────────────
    private CircleImageView navHeaderAvatar;
    private TextView navHeaderName;
    private TextView navHeaderRole;

    // ── Firebase ──────────────────────────────────────────────────────────
    private DatabaseReference userChatsRef;
    private DatabaseReference chatsRef;
    private DatabaseReference usersRef;
    private ValueEventListener userChatsListener;

    // ── Data ──────────────────────────────────────────────────────────────
    private String authUid;
    private ChatListAdapter adapter;
    private final List<ChatModel> chatList = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────
    // Fragment lifecycle
    // ─────────────────────────────────────────────────────────────────────
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Verify session via FirebaseAuth (single source of truth)
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            navigateToLogin();
            return;
        }
        authUid = firebaseUser.getUid();

        // 2. Bind views
        bindViews(view);

        // 3. Set up RecyclerView
        setupRecyclerView();

        // 4. Set up Navigation Drawer
        setupNavigationDrawer();

        // 5. Init Firebase references with keepSynced
        initFirebase();

        // 6. Load nav header (user profile)
        loadNavHeader();

        // 7. Load chat list
        loadChatList();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove Firebase listener to prevent memory leaks
        if (userChatsRef != null && userChatsListener != null) {
            userChatsRef.removeEventListener(userChatsListener);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (userChatsRef != null && userChatsListener != null) {
            userChatsRef.removeEventListener(userChatsListener);
        }
        if (authUid != null) {
            loadChatList();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Setup helpers
    // ─────────────────────────────────────────────────────────────────────
    private void bindViews(View view) {
        drawerLayout = view.findViewById(R.id.drawer_layout);
        recyclerView = view.findViewById(R.id.chat_recycler_view);
        emptyStateView = view.findViewById(R.id.empty_state_view);
        navigationView = view.findViewById(R.id.navigation_view);

        // Nav header child views
        View headerView = navigationView.getHeaderView(0);
        navHeaderAvatar = headerView.findViewById(R.id.nav_header_avatar);
        navHeaderName = headerView.findViewById(R.id.nav_header_name);
        navHeaderRole = headerView.findViewById(R.id.nav_header_role);
    }

    /**
     * Called by MainActivity when toolbar navigation icon is clicked
     */
    public void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(navigationView);
        }
    }

    private void setupRecyclerView() {
        adapter = new ChatListAdapter(requireContext(), chatList);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(false);

        adapter.setOnChatClickListener(chat -> {
            Intent intent = new Intent(requireContext(), com.example.cmcs.ChatActivity.class);
            intent.putExtra("chatId", chat.getChatId());
            
            boolean isGroup = "group".equals(chat.getType());
            if (isGroup) {
                // For group chats
                intent.putExtra("otherUid", chat.getChatId()); // Use chatId as placeholder
                intent.putExtra("otherName", chat.getName()); // Use group name
                intent.putExtra("isGroup", true);
            } else {
                // For private chats
                intent.putExtra("otherUid", chat.getOtherUserId());
                intent.putExtra("otherName", chat.getOtherUserName());
                intent.putExtra("otherAvatar", chat.getOtherUserProfileImage());
            }
            
            intent.putExtra("fromChatList", true);
            startActivity(intent);
        });

        adapter.setOnChatLongClickListener(chat -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete chat")
                    .setMessage("Are you sure you want to delete this chat?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dialog, which) -> deleteChat(chat))
                    .show();
        });

    }

    private void setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawer(navigationView);

            int id = item.getItemId();

            if (id == R.id.drawer_profile) {
                Toast.makeText(requireContext(),
                        "Profile — coming soon!", Toast.LENGTH_SHORT).show();

            } else if (id == R.id.drawer_settings) {
                Toast.makeText(requireContext(),
                        "Settings — coming soon!", Toast.LENGTH_SHORT).show();

            } else if (id == R.id.drawer_logout) {
                handleLogout();

            } else if (id == R.id.drawer_about) {
                Toast.makeText(requireContext(),
                        "CMCS — College of Management & Computer Science",
                        Toast.LENGTH_LONG).show();
            }

            return true;
        });
    }

    private void initFirebase() {
        FirebaseDatabase db = FirebaseDatabase.getInstance();

        userChatsRef = db.getReference("userChats").child(authUid);
        chatsRef = db.getReference("chats");
        usersRef = db.getReference("users");

        // Keep offline cache warm
        userChatsRef.keepSynced(true);
        chatsRef.keepSynced(true);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Firebase data loading
    // ─────────────────────────────────────────────────────────────────────
    /**
     * Loads and populates the nav header with the current user's profile
     * fetched from users/<authUid>.
     */
    private void loadNavHeader() {
        usersRef.child(authUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) {
                    return;
                }

                String name = snapshot.child("name").getValue(String.class);
                String imgUrl = snapshot.child("profileImage").getValue(String.class);
                String userRole = snapshot.child("role").getValue(String.class);

                navHeaderName.setText(name != null ? name : "Unknown User");
                navHeaderRole.setText("teacher".equalsIgnoreCase(userRole) ? "Teacher" : "Student");

                if (imgUrl != null && !imgUrl.isEmpty()) {
                    Glide.with(ChatsFragment.this)
                            .load(imgUrl)
                            .apply(new RequestOptions()
                                    .placeholder(R.drawable.ic_drawer_profile)
                                    .circleCrop())
                            .into(navHeaderAvatar);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Non-critical – header stays at defaults
            }
        });
    }

    /**
     * Main chat list loader.
     *
     * Listens to userChats/<authUid> in real time. For each chatId: fetches
     * chats/<chatId>, then (for private chats) fetches users/<otherUid> to
     * populate display name + profile image. Sorts by lastTimestamp DESC after
     * all fetches complete.
     */
    private void loadChatList() {
        userChatsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot userChatsSnapshot) {
                if (!isAdded()) {
                    return;
                }

                // Gather all visible chatIds + their unread counts
                List<String> chatIds = new ArrayList<>();
                // Store unread per chatId for later assignment
                java.util.HashMap<String, Integer> unreadMap = new java.util.HashMap<>();
                for (DataSnapshot child : userChatsSnapshot.getChildren()) {
                    Boolean visible = child.child("visible").getValue(Boolean.class);
                    if (Boolean.TRUE.equals(visible)) {
                        String chatId = child.getKey();
                        chatIds.add(chatId);
                        Long unread = child.child("unreadCount").getValue(Long.class);
                        unreadMap.put(chatId, unread != null ? unread.intValue() : 0);
                    }
                }

                if (chatIds.isEmpty()) {
                    chatList.clear();
                    adapter.notifyDataSetChanged();
                    toggleEmptyState(true);
                    return;
                }

                // Fetch each chat's details
                List<ChatModel> tempList = new ArrayList<>();
                AtomicInteger pending = new AtomicInteger(chatIds.size());

                for (String chatId : chatIds) {
                    chatsRef.child(chatId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot chatSnap) {
                                    if (!isAdded()) {
                                        return;
                                    }
                                    if (!chatSnap.exists()) {
                                        checkAndPublish(pending, tempList);
                                        return;
                                    }

                                    ChatModel chat = chatSnap.getValue(ChatModel.class);
                                    if (chat == null) {
                                        checkAndPublish(pending, tempList);
                                        return;
                                    }
                                    chat.setChatId(chatSnap.getKey());

                                    // Attach unread count from userChats node
                                    int unread = unreadMap.containsKey(chatSnap.getKey())
                                            ? unreadMap.get(chatSnap.getKey()) : 0;
                                    chat.setUnreadCount(unread);

                                    boolean isPrivate = "private".equals(chat.getType());
                                    if (isPrivate) {
                                        // Determine the other participant's UID
                                        String otherUid = getOtherParticipantUid(chat);
                                        if (otherUid != null) {
                                            chat.setOtherUserId(otherUid);
                                            fetchUserProfile(chat, tempList, pending);
                                        } else {
                                            tempList.add(chat);
                                            checkAndPublish(pending, tempList);
                                        }
                                    } else {
                                        // Group chat — no need to fetch other user
                                        tempList.add(chat);
                                        checkAndPublish(pending, tempList);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    checkAndPublish(pending, tempList);
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(),
                            "Failed to load chats: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        };

        userChatsRef.addValueEventListener(userChatsListener);
    }

    /**
     * Fetches users/<otherUid> and populates runtime fields on the ChatModel.
     * Decrements the pending counter and publishes when all fetches are done.
     */
    private void fetchUserProfile(ChatModel chat, List<ChatModel> tempList,
            AtomicInteger pending) {
        usersRef.child(chat.getOtherUserId())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot userSnap) {
                        if (!isAdded()) {
                            return;
                        }

                        chat.setOtherUserName(
                                userSnap.child("name").getValue(String.class));
                        chat.setOtherUserProfileImage(
                                userSnap.child("profileImage").getValue(String.class));
                        chat.setOtherUserRole(
                                userSnap.child("role").getValue(String.class));

                        tempList.add(chat);
                        checkAndPublish(pending, tempList);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        tempList.add(chat); // add with incomplete info
                        checkAndPublish(pending, tempList);
                    }
                });
    }

    /**
     * Decrements the pending counter. When it reaches 0, sorts and publishes to
     * adapter.
     */
    private void checkAndPublish(AtomicInteger pending, List<ChatModel> tempList) {
        if (!isAdded()) {
            return;
        }
        if (pending.decrementAndGet() == 0) {
            // Sort newest first
            Collections.sort(tempList,
                    (a, b) -> Long.compare(b.getLastTimestamp(), a.getLastTimestamp()));

            chatList.clear();
            chatList.addAll(tempList);
            adapter.notifyDataSetChanged();
            toggleEmptyState(chatList.isEmpty());
            if (!chatList.isEmpty()) {
                recyclerView.scrollToPosition(0);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Utility helpers
    // ─────────────────────────────────────────────────────────────────────
    /**
     * Returns the UID of the other participant in a private chat. Iterates the
     * participants map and returns the key that is NOT authUid.
     */
    private String getOtherParticipantUid(ChatModel chat) {
        Map<String, Boolean> participants = chat.getParticipants();
        if (participants == null) {
            return null;
        }

        for (String uid : participants.keySet()) {
            if (!uid.equals(authUid)) {
                return uid;
            }
        }
        return null;
    }

    private void toggleEmptyState(boolean isEmpty) {
        emptyStateView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void deleteChat(ChatModel chat) {
        String chatId = chat.getChatId();
        if (chatId == null) {
            return;
        }

        String otherUid = getOtherParticipantUid(chat);

        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("messages/" + chatId, null);
        updates.put("chats/" + chatId, null);
        updates.put("userChats/" + authUid + "/" + chatId, null);
        if (otherUid != null) {
            updates.put("userChats/" + otherUid + "/" + chatId, null);
        }

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded()) {
                        return;
                    }
                    int index = -1;
                    for (int i = 0; i < chatList.size(); i++) {
                        if (chatId.equals(chatList.get(i).getChatId())) {
                            index = i;
                            break;
                        }
                    }
                    if (index != -1) {
                        chatList.remove(index);
                        adapter.notifyItemRemoved(index);
                        toggleEmptyState(chatList.isEmpty());
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "Failed to delete chat: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Auth
    // ─────────────────────────────────────────────────────────────────────
    private void handleLogout() {
        // Clear SharedPreferences
        requireActivity()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply();

        // Sign out from Firebase Auth
        FirebaseAuth.getInstance().signOut();

        // Navigate to Login
        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(requireContext(), Login.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
