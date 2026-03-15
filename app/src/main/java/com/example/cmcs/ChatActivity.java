package com.example.cmcs;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.cmcs.adapters.MessageAdapter;
import com.example.cmcs.models.MessageModel;
import com.example.cmcs.utils.CloudinaryUploader;
import com.example.cmcs.utils.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * ChatActivity — the individual chat screen.
 *
 * Receives via Intent: "chatId" – Firebase chat node key "otherUid" – UID of
 * the other participant "otherName" – Display name of the other participant
 * "otherAvatar" – (optional) Profile image URL
 *
 * Firebase nodes used: messages/<chatId> – live message list
 * chats/<chatId>/lastMessage – updated on send chats/<chatId>/lastTimestamp –
 * updated on send
 *
 * Message status lifecycle: Push message → no deliveredTo entry → WHITE dot
 * Receiver enters → adds deliveredTo/<myUid>:true → RED dot Receiver stays open
 * → adds readBy/<myUid>:true → GREEN dot
 *
 * Performance: – DiffUtil in adapter (no full notifyDataSetChanged) – Single
 * ValueEventListener (removed in onDestroy) – keepSynced on messages node for
 * offline support
 */
public class ChatActivity extends AppCompatActivity {

    // ── Intent keys ──────────────────────────────────────────────────────────
    public static final String EXTRA_CHAT_ID = "chatId";
    public static final String EXTRA_OTHER_UID = "otherUid";
    public static final String EXTRA_OTHER_NAME = "otherName";
    public static final String EXTRA_OTHER_AVATAR = "otherAvatar";

    // ── Firebase ─────────────────────────────────────────────────────────────
    private DatabaseReference messagesRef;
    private DatabaseReference chatRef;
    private ValueEventListener messageListener;

    // ── State ─────────────────────────────────────────────────────────────────
    private String myUid;
    private String chatId;
    private String otherUid;

    // ── UI ────────────────────────────────────────────────────────────────────
    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageView sendVoice;
    private ImageView btnSend;
    private MessageAdapter adapter;
    private LinearLayoutManager layoutManager;

    // ── File picker ──────────────────────────────────────────────────────────
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> videoPickerLauncher;
    private ActivityResultLauncher<String> documentPickerLauncher;
    private ActivityResultLauncher<String> filePickerLauncher;

    // ── Reply Logic ──────────────────────────────────────────────
    private View layoutReplyPreview;
    private TextView tvReplyPreviewText;
    private MessageModel replyingToMessage = null;
    private boolean isForwarding = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Immersive Edge-to-Edge Setup
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Configures modern immersive edge-to-edge layout for chat screen.
     * Makes status bar transparent and extends toolbar into status bar area.
     * Handles keyboard (IME) insets to keep input field above keyboard.
     */
    private void setupImmersiveEdgeToEdge() {
        // Enable edge-to-edge mode
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        // Make status bar transparent
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        
        // Set light status bar icons (white icons on purple background)
        androidx.core.view.WindowInsetsControllerCompat controller = 
            androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
        }
        
        // Apply insets to handle both status bar and keyboard
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                // Get system bars insets (status bar, navigation bar)
                androidx.core.graphics.Insets systemBars = 
                    insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                
                // Get IME (keyboard) insets
                androidx.core.graphics.Insets imeInsets = 
                    insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
                
                // Apply status bar inset to toolbar
                View toolbar = findViewById(R.id.chatHeader);
                if (toolbar != null) {
                    // Base padding of 8dp converted to pixels
                    int basePaddingPx = (int) (8 * getResources().getDisplayMetrics().density);
                    toolbar.setPadding(
                        toolbar.getPaddingLeft(),
                        systemBars.top + basePaddingPx,
                        toolbar.getPaddingRight(),
                        toolbar.getPaddingBottom()
                    );
                }
                
                // Apply keyboard inset to input container to keep it above keyboard
                View inputContainer = findViewById(R.id.chatInputContainer);
                if (inputContainer != null) {
                    // Get original bottom padding (8dp)
                    int originalBottomPaddingPx = (int) (8 * getResources().getDisplayMetrics().density);
                    // Use the larger of IME bottom or navigation bar bottom, plus original padding
                    int bottomInset = Math.max(imeInsets.bottom, systemBars.bottom);
                    inputContainer.setPadding(
                        inputContainer.getPaddingLeft(),
                        inputContainer.getPaddingTop(),
                        inputContainer.getPaddingRight(),
                        bottomInset > 0 ? bottomInset : originalBottomPaddingPx
                    );
                }
                
                return insets;
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_r);

        // Apply modern immersive edge-to-edge layout
        setupImmersiveEdgeToEdge();

        // Initialize file pickers
        initializeFilePickers();

        // 1. Auth check
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            finish();
            return;
        }
        myUid = firebaseUser.getUid();

        // 2. Unpack Intent extras
        chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
        otherUid = getIntent().getStringExtra(EXTRA_OTHER_UID);
        String otherName = getIntent().getStringExtra(EXTRA_OTHER_NAME);
        String otherAvatar = getIntent().getStringExtra(EXTRA_OTHER_AVATAR);

        if (chatId == null || otherUid == null) {
            Toast.makeText(this, "Invalid chat parameters", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 3. Firebase references
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        messagesRef = db.getReference("messages").child(chatId);
        chatRef = db.getReference("chats").child(chatId);

        // Enable offline support for this chat's messages
        messagesRef.keepSynced(true);

        // 4. Bind views and set up toolbar
        bindViews(otherName, otherAvatar);

        // Check for forwarded text
        String forwardedText = getIntent().getStringExtra("EXTRA_FORWARDED_TEXT");
        if (forwardedText != null && !forwardedText.isEmpty()) {

            etMessage.setText(forwardedText);
            isForwarding = true;

            etMessage.setSelection(forwardedText.length());
            etMessage.requestFocus();

            sendVoice.setVisibility(View.GONE);
            btnSend.setVisibility(View.VISIBLE);
        }

        // 5. Set up RecyclerView
        adapter = new MessageAdapter(myUid, otherUid);
        adapter.setOnMessageLongClickListener(message -> showMessageOptions(message));
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);   // newest messages at bottom
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);

        // 6. Mic ↔ Send toggle via TextWatcher
        etMessage.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                if (s.toString().trim().isEmpty()) {

                    sendVoice.setVisibility(View.VISIBLE);
                    btnSend.setVisibility(View.GONE);

                } else {

                    sendVoice.setVisibility(View.GONE);
                    btnSend.setVisibility(View.VISIBLE);

                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 7. Send button
        btnSend.setOnClickListener(v -> {

            String text = etMessage.getText().toString().trim();

            if (!text.isEmpty()) {
                sendMessage(text);
            }

        });

        // 7.2 Send voice message
        sendVoice.setOnClickListener(v
                -> Toast.makeText(this, "Voice message feature coming soon", Toast.LENGTH_SHORT).show()
        );
        // 8. Emoji / Attach placeholders
        findViewById(R.id.btn_emoji).setOnClickListener(v
                -> Toast.makeText(this, "Emoji picker coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btn_attach).setOnClickListener(v -> showAttachmentPicker());

        // 9. Load messages
        verifyChatExists();

        // 10. Mark as delivered when we open this chat
        markDelivered();

        // 11. Reset my unread count immediately on open
        resetUnreadCount();

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Mark all messages as read while we are actively looking at the chat
        markRead();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove listener to avoid memory leaks
        if (messageListener != null) {
            messagesRef.removeEventListener(messageListener);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // View binding
    // ─────────────────────────────────────────────────────────────────────────
    private void bindViews(String otherName, String otherAvatar) {

        rvMessages = findViewById(R.id.chatRecyclerView);
        etMessage = findViewById(R.id.messageInput);

        sendVoice = findViewById(R.id.sendVoice);
        btnSend = findViewById(R.id.btn_send);

        layoutReplyPreview = findViewById(R.id.layout_reply_preview);
        tvReplyPreviewText = findViewById(R.id.tv_reply_preview_text);

        findViewById(R.id.btn_cancel_reply).setOnClickListener(v -> clearReplyPreview());

        TextView tvName = findViewById(R.id.username);
        if (otherName != null) {
            tvName.setText(otherName);
        }

        CircleImageView ivAvatar = findViewById(R.id.userImage);

        if (otherAvatar != null && !otherAvatar.isEmpty()) {
            Glide.with(this)
                    .load(otherAvatar)
                    .apply(new RequestOptions()
                            .placeholder(R.drawable.ic_user)
                            .error(R.drawable.ic_user))
                    .into(ivAvatar);
        }

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Message loading (real-time)
    // ─────────────────────────────────────────────────────────────────────────
    private void listenToMessages() {
        messageListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<MessageModel> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    MessageModel msg = child.getValue(MessageModel.class);
                    if (msg != null) {
                        msg.setMessageId(child.getKey());
                        list.add(msg);
                    }
                }
                adapter.submitList(list);
                // Scroll to bottom on new message
                if (!list.isEmpty()) {
                    rvMessages.post(()
                            -> rvMessages.scrollToPosition(list.size() - 1));
                }
                // Mark as read if we receive new messages while chat is open
                markRead();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this,
                        "Error loading messages: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        // Order by timestamp — Firebase sorts ascending by default on a numeric child
        messagesRef.orderByChild("timestamp").addValueEventListener(messageListener);
    }

    private void verifyChatExists() {
        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() && getIntent().getBooleanExtra("fromChatList", false)) {
                    Toast.makeText(ChatActivity.this,
                            "Chat no longer exists", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    listenToMessages();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                finish();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Selection Actions
    // ─────────────────────────────────────────────────────────────────────────
    private void clearReplyPreview() {
        replyingToMessage = null;
        layoutReplyPreview.setVisibility(View.GONE);
    }

    private void showReplyPreview(MessageModel message) {
        if (message != null) {
            replyingToMessage = message;
            layoutReplyPreview.setVisibility(View.VISIBLE);
            tvReplyPreviewText.setText(message.getMessage());
            etMessage.requestFocus();
        }
    }

    private void deleteMessage(MessageModel message) {
        if (message != null && message.getMessageId() != null) {
            messagesRef.child(message.getMessageId()).removeValue()
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void forwardMessage(MessageModel message) {
        if (message != null) {
            Intent intent = new Intent(this, ForwardMessageActivity.class);
            intent.putExtra("EXTRA_FORWARDED_TEXT", message.getMessage());
            startActivity(intent);
        }
    }

    private void showMessageOptions(MessageModel message) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottomsheet_message_options, null);
        dialog.setContentView(sheet);

        sheet.findViewById(R.id.option_reply).setOnClickListener(v -> {
            dialog.dismiss();
            showReplyPreview(message);
        });

        sheet.findViewById(R.id.option_forward).setOnClickListener(v -> {
            dialog.dismiss();
            forwardMessage(message);
        });

        sheet.findViewById(R.id.option_copy).setOnClickListener(v -> {
            dialog.dismiss();
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Message", message.getMessage());
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show();
            }
        });

        sheet.findViewById(R.id.option_delete).setOnClickListener(v -> {
            dialog.dismiss();
            deleteMessage(message);
        });

        dialog.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send message
    // ─────────────────────────────────────────────────────────────────────────
    private void sendMessage(String text) {
        // Clear input immediately for snappy UX
        etMessage.setText("");

        // Build message payload
        String messageId = messagesRef.push().getKey();
        if (messageId == null) {
            return;
        }

        Map<String, Object> msgData = new HashMap<>();
        msgData.put("senderId", myUid);
        msgData.put("message", text);
        msgData.put("timestamp", ServerValue.TIMESTAMP);

        if (replyingToMessage != null) {
            msgData.put("replyToMessageId", replyingToMessage.getMessageId());
            msgData.put("replyToText", replyingToMessage.getMessage());
            msgData.put("replyToSenderId", replyingToMessage.getSenderId());
            clearReplyPreview();
        }

        if (isForwarding) {
            msgData.put("forwarded", true);
            isForwarding = false; // Reset for subsequent messages
        }

        // deliveredTo and readBy start empty
        // ── Single atomic multi-path write ───────────────────────────────────
        // Collapses: message write + chat summary + receiver's userChats entry
        // into ONE updateChildren() call on the root.
        //
        // Root cause of intermittent failure: the old code called
        // incrementUnreadForOthers() in an onSuccess callback — 4 async steps
        // (read participants, read userChats/<uid>/<chatId>, compute, write).
        // Any step could fail silently. If userChats/<receiverUid>/<chatId>
        // never got visible:true, ChatsFragment would never show the chat.
        //
        // Fix: ServerValue.increment(1) is a Firebase server-side atomic
        // increment — no prior read required. visible:true is always written
        // in the same call, guaranteeing the node exists for ChatsFragment.
        Map<String, Object> update = new HashMap<>();
        update.put("messages/" + chatId + "/" + messageId, msgData);
        update.put("chats/" + chatId + "/lastMessage", text);
        update.put("chats/" + chatId + "/lastTimestamp", ServerValue.TIMESTAMP);

        // Ensure chat metadata exists for ChatsFragment when the conversation is created/updated
        update.put("chats/" + chatId + "/type", "private");
        update.put("chats/" + chatId + "/participants/" + myUid, true);
        update.put("chats/" + chatId + "/participants/" + otherUid, true);

        // Guarantee receiver's userChats entry exists with visible=true
        update.put("userChats/" + otherUid + "/" + chatId + "/visible", true);
        // Atomically increment receiver's unread count — no read step needed
        update.put("userChats/" + otherUid + "/" + chatId + "/unreadCount",
                ServerValue.increment(1));
        // Reset my own unread count to 0 (I'm the sender)
        update.put("userChats/" + myUid + "/" + chatId + "/visible", true);
        update.put("userChats/" + myUid + "/" + chatId + "/unreadCount", 0);

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(update)
                .addOnFailureListener(e
                        -> Toast.makeText(this, "Failed to send: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unread count helpers
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Resets my unread count to 0 immediately when I open this chat. Called in
     * onCreate().
     */
    private void resetUnreadCount() {
        FirebaseDatabase.getInstance().getReference()
                .child("userChats")
                .child(myUid)
                .child(chatId)
                .child("unreadCount")
                .setValue(0);
    }

    /**
     * Increments unreadCount for every participant except the sender. Uses a
     * read-then-write for each participant (group-chat safe). Called after a
     * message is successfully sent.
     */
    private void incrementUnreadForOthers() {
        // Fetch the participants list from chats/<chatId>/participants
        FirebaseDatabase.getInstance().getReference()
                .child("chats").child(chatId).child("participants")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String uid = child.getKey();
                            if (uid == null || uid.equals(myUid)) {
                                continue;
                            }

                            DatabaseReference userChatRef = FirebaseDatabase.getInstance()
                                    .getReference("userChats").child(uid).child(chatId);

                            // Read the full node so we can guarantee visible=true + increment unread
                            userChatRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot ds) {
                                    Map<String, Object> updates = new HashMap<>();
                                    // Ensure visible=true (creates node if it was missing)
                                    Boolean visible = ds.child("visible").getValue(Boolean.class);
                                    if (!Boolean.TRUE.equals(visible)) {
                                        updates.put("visible", true);
                                    }
                                    // Increment unread count
                                    Long current = ds.child("unreadCount").getValue(Long.class);
                                    updates.put("unreadCount", (current != null ? current : 0) + 1);
                                    userChatRef.updateChildren(updates);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    /* silent */ }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        /* silent */ }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status: Delivered & Read
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Marks all messages in this chat where we are NOT the sender as delivered.
     * Called once when ChatActivity is opened.
     */
    private void markDelivered() {
        messagesRef.orderByChild("timestamp")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            MessageModel msg = child.getValue(MessageModel.class);
                            if (msg == null) {
                                continue;
                            }
                            // Only mark messages from the other user
                            if (!myUid.equals(msg.getSenderId())) {
                                updates.put("messages/" + chatId + "/"
                                        + child.getKey() + "/deliveredTo/" + myUid, true);
                            }
                        }
                        if (!updates.isEmpty()) {
                            FirebaseDatabase.getInstance().getReference()
                                    .updateChildren(updates);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        /* silent */ }
                });
    }

    /**
     * Marks all received messages as read. Called in onResume so the green dot
     * appears as soon as user looks at chat.
     */
    private void markRead() {
        messagesRef.orderByChild("timestamp")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            MessageModel msg = child.getValue(MessageModel.class);
                            if (msg == null) {
                                continue;
                            }
                            if (!myUid.equals(msg.getSenderId())) {
                                // Only update if not already marked read
                                if (msg.getReadBy() == null
                                        || !Boolean.TRUE.equals(msg.getReadBy().get(myUid))) {
                                    updates.put("messages/" + chatId + "/"
                                            + child.getKey() + "/readBy/" + myUid, true);
                                }
                            }
                        }
                        if (!updates.isEmpty()) {
                            FirebaseDatabase.getInstance().getReference()
                                    .updateChildren(updates);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        /* silent */ }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static helper: build Intent from SelectUserActivity
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Convenience factory for launching ChatActivity.
     *
     * Usage in SelectUserActivity.openChat():
     * startActivity(ChatActivity.newIntent(this, chatId, otherUid,
     * other.getName(), other.getProfileImage()));
     */
    public static Intent newIntent(android.content.Context ctx,
            String chatId,
            String otherUid,
            String otherName,
            String otherAvatar) {
        Intent i = new Intent(ctx, ChatActivity.class);
        i.putExtra(EXTRA_CHAT_ID, chatId);
        i.putExtra(EXTRA_OTHER_UID, otherUid);
        i.putExtra(EXTRA_OTHER_NAME, otherName);
        i.putExtra(EXTRA_OTHER_AVATAR, otherAvatar);
        return i;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attachment System
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Initialize file picker launchers for different attachment types
     */
    private void initializeFilePickers() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadAttachment(uri, MessageModel.TYPE_IMAGE, "chat_images");
                    }
                });

        videoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadAttachment(uri, MessageModel.TYPE_VIDEO, "chat_videos");
                    }
                });

        documentPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadAttachment(uri, MessageModel.TYPE_FILE, "chat_files");
                    }
                });

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadAttachment(uri, MessageModel.TYPE_FILE, "chat_files");
                    }
                });
    }

    /**
     * Show attachment picker bottom sheet
     */
    private void showAttachmentPicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottomsheet_attachment_picker, null);
        dialog.setContentView(sheet);

        sheet.findViewById(R.id.option_image).setOnClickListener(v -> {
            dialog.dismiss();
            imagePickerLauncher.launch("image/*");
        });

        sheet.findViewById(R.id.option_video).setOnClickListener(v -> {
            dialog.dismiss();
            videoPickerLauncher.launch("video/*");
        });

        sheet.findViewById(R.id.option_document).setOnClickListener(v -> {
            dialog.dismiss();
            documentPickerLauncher.launch("application/pdf");
        });

        sheet.findViewById(R.id.option_file).setOnClickListener(v -> {
            dialog.dismiss();
            filePickerLauncher.launch("*/*");
        });

        dialog.show();
    }

    /**
     * Upload attachment to Cloudinary and send message
     */
    private void uploadAttachment(Uri uri, String messageType, String folder) {
        // Show uploading toast
        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show();

        // Get file name
        String fileName = getFileName(uri);

        CloudinaryUploader.upload(
                this,
                uri,
                folder,
                new CloudinaryUploader.Callback() {
                    @Override
                    public void onProgress(int percent) {
                        // Could show progress bar here
                    }

                    @Override
                    public void onSuccess(String secureUrl) {
                        runOnUiThread(() -> {
                            sendMediaMessage(messageType, secureUrl, fileName);
                            Toast.makeText(ChatActivity.this,
                                    "Uploaded successfully",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onFailure(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(ChatActivity.this,
                                    "Upload failed: " + error,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    /**
     * Send media message to Firebase
     */
    private void sendMediaMessage(String messageType, String mediaUrl, String fileName) {
        String messageId = messagesRef.push().getKey();
        if (messageId == null) {
            return;
        }

        Map<String, Object> msgData = new HashMap<>();
        msgData.put("senderId", myUid);
        msgData.put("messageType", messageType);
        msgData.put("mediaUrl", mediaUrl);
        
        if (fileName != null && !fileName.isEmpty()) {
            msgData.put("fileName", fileName);
        }
        
        msgData.put("timestamp", ServerValue.TIMESTAMP);

        // Build multi-path update
        Map<String, Object> update = new HashMap<>();
        update.put("messages/" + chatId + "/" + messageId, msgData);
        
        // Update last message preview based on type
        String lastMessagePreview;
        switch (messageType) {
            case MessageModel.TYPE_IMAGE:
                lastMessagePreview = "📷 Image";
                break;
            case MessageModel.TYPE_VIDEO:
                lastMessagePreview = "🎥 Video";
                break;
            case MessageModel.TYPE_FILE:
                lastMessagePreview = "📎 " + (fileName != null ? fileName : "File");
                break;
            default:
                lastMessagePreview = "Attachment";
                break;
        }
        
        update.put("chats/" + chatId + "/lastMessage", lastMessagePreview);
        update.put("chats/" + chatId + "/lastTimestamp", ServerValue.TIMESTAMP);

        // Ensure chat metadata exists
        update.put("chats/" + chatId + "/type", "private");
        update.put("chats/" + chatId + "/participants/" + myUid, true);
        update.put("chats/" + chatId + "/participants/" + otherUid, true);

        // Update receiver's userChats
        update.put("userChats/" + otherUid + "/" + chatId + "/visible", true);
        update.put("userChats/" + otherUid + "/" + chatId + "/unreadCount",
                ServerValue.increment(1));
        
        // Reset my own unread count
        update.put("userChats/" + myUid + "/" + chatId + "/visible", true);
        update.put("userChats/" + myUid + "/" + chatId + "/unreadCount", 0);

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(update)
                .addOnFailureListener(e
                        -> Toast.makeText(this, "Failed to send: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
    }

    /**
     * Get file name from URI
     */
    private String getFileName(Uri uri) {
        String fileName = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(
                    uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (fileName == null) {
            fileName = uri.getLastPathSegment();
        }
        return fileName;
    }
}
