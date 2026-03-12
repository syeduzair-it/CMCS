package com.example.cmcs;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.adapters.ChatListAdapter;
import com.example.cmcs.models.ChatModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ForwardMessageActivity extends AppCompatActivity {

    private RecyclerView rvChats;
    private ChatListAdapter adapter;
    private List<ChatModel> chatList;
    private String forwardedText;
    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forward_message);

        forwardedText = getIntent().getStringExtra("EXTRA_FORWARDED_TEXT");
        if (forwardedText == null || forwardedText.isEmpty()) {
            Toast.makeText(this, "No message to forward", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }
        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        MaterialToolbar toolbar = findViewById(R.id.toolbar_forward);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvChats = findViewById(R.id.rv_forward_chats);
        rvChats.setLayoutManager(new LinearLayoutManager(this));
        chatList = new ArrayList<>();
        adapter = new ChatListAdapter(this, chatList);
        rvChats.setAdapter(adapter);

        adapter.setOnChatClickListener(chat -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CHAT_ID, chat.getChatId());
            intent.putExtra(ChatActivity.EXTRA_OTHER_UID, chat.getOtherUserId());
            intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, chat.getDisplayName());
            intent.putExtra(ChatActivity.EXTRA_OTHER_AVATAR, chat.getOtherUserProfileImage());
            intent.putExtra("EXTRA_FORWARDED_TEXT", forwardedText);
            startActivity(intent);
            finish();
        });

        loadConversations();
    }

    private void loadConversations() {
        DatabaseReference userChatsRef = FirebaseDatabase.getInstance().getReference("userChats").child(myUid);
        userChatsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                chatList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String chatId = ds.getKey();
                    if (chatId == null) {
                        continue;
                    }

                    Boolean visible = ds.child("visible").getValue(Boolean.class);
                    if (visible == null || !visible) {
                        continue;
                    }

                    fetchChatDetails(chatId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ForwardMessageActivity.this, "Failed to load chats", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchChatDetails(String chatId) {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child("chats").child(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot chatSnap) {
                if (!chatSnap.exists()) {
                    return;
                }

                String type = chatSnap.child("type").getValue(String.class);
                if (!"private".equals(type)) {
                    return;
                }

                String lastMessage = chatSnap.child("lastMessage").getValue(String.class);
                Long lastTimestamp = chatSnap.child("lastTimestamp").getValue(Long.class);

                String otherUid = null;
                for (DataSnapshot p : chatSnap.child("participants").getChildren()) {
                    if (!p.getKey().equals(myUid)) {
                        otherUid = p.getKey();
                        break;
                    }
                }
                if (otherUid == null) {
                    return;
                }

                final String fOtherUid = otherUid;
                db.child("users").child(otherUid).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot userSnap) {
                        if (!userSnap.exists()) {
                            return;
                        }

                        String name = userSnap.child("name").getValue(String.class);
                        String avatar = userSnap.child("profileImage").getValue(String.class);
                        String role = userSnap.child("role").getValue(String.class);

                        ChatModel model = new ChatModel();
                        model.setChatId(chatId);
                        model.setOtherUserId(fOtherUid);
                        model.setOtherUserName(name != null ? name : "Unknown");
                        model.setOtherUserProfileImage(avatar);
                        model.setOtherUserRole(role);
                        model.setLastMessage(lastMessage);
                        model.setLastTimestamp(lastTimestamp != null ? lastTimestamp : 0);
                        model.setUnreadCount(0); // Not tracked in forward screen

                        chatList.add(model);
                        Collections.sort(chatList, (c1, c2) -> Long.compare(c2.getLastTimestamp(), c1.getLastTimestamp()));
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }
}
