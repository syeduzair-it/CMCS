package com.example.cmcs.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.Map;

/**
 * Maps to the Firebase node: chats/<chatId>
 *
 * Transient fields (otherUserName, otherUserProfileImage, otherUserRole) are
 * populated at runtime from users/<uid> and are NEVER written to Firebase.
 */
@IgnoreExtraProperties
public class ChatModel {

    // ── Firebase-persisted fields ──────────────────────────────────────────
    private String chatId;
    private String type;            // "private" | "group"
    private String name;            // group name (for private chats this may be empty)
    private String createdBy;
    private Map<String, Boolean> participants;
    private String lastMessage;
    private long lastTimestamp;

    // ── Runtime-only fields (never stored in Firebase) ─────────────────────
    @Exclude
    private String otherUserId;
    @Exclude
    private String otherUserName;
    @Exclude
    private String otherUserProfileImage;
    @Exclude
    private String otherUserRole;
    @Exclude
    private int unreadCount;

    // Required empty constructor for Firebase deserialization
    public ChatModel() {
    }

    // ── Getters / Setters ──────────────────────────────────────────────────
    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Map<String, Boolean> getParticipants() {
        return participants;
    }

    public void setParticipants(Map<String, Boolean> participants) {
        this.participants = participants;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public long getLastTimestamp() {
        return lastTimestamp;
    }

    public void setLastTimestamp(long lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    }

    @Exclude
    public String getOtherUserId() {
        return otherUserId;
    }

    @Exclude
    public void setOtherUserId(String otherUserId) {
        this.otherUserId = otherUserId;
    }

    @Exclude
    public String getOtherUserName() {
        return otherUserName;
    }

    @Exclude
    public void setOtherUserName(String otherUserName) {
        this.otherUserName = otherUserName;
    }

    @Exclude
    public String getOtherUserProfileImage() {
        return otherUserProfileImage;
    }

    @Exclude
    public void setOtherUserProfileImage(String otherUserProfileImage) {
        this.otherUserProfileImage = otherUserProfileImage;
    }

    @Exclude
    public String getOtherUserRole() {
        return otherUserRole;
    }

    @Exclude
    public void setOtherUserRole(String otherUserRole) {
        this.otherUserRole = otherUserRole;
    }

    @Exclude
    public int getUnreadCount() {
        return unreadCount;
    }

    @Exclude
    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    /**
     * Derives the display name: – For a private chat, uses the fetched
     * otherUserName. – For a group chat, uses the group name stored in
     * Firebase.
     */
    @Exclude
    public String getDisplayName() {
        if ("group".equals(type)) {
            return name != null ? name : "Group";
        }
        return otherUserName != null ? otherUserName : "Unknown";
    }
}
