package com.example.cmcs.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps to the Firebase node: messages/<chatId>/<messageId>
 *
 * {
 * senderId: "uid" 
 * messageType: "text" | "image" | "video" | "file"
 * message: "text" (for text messages)
 * mediaUrl: "cloudinary url" (for media messages)
 * fileName: "document.pdf" (for file messages)
 * timestamp: 1234567890 
 * deliveredTo: { uid: true } 
 * readBy: { uid: true }
 * }
 *
 * Message status is derived from deliveredTo / readBy maps: WHITE = not yet in
 * deliveredTo for other user RED = in deliveredTo but not readBy GREEN = in
 * readBy
 *
 * The transient field messageId is never written to Firebase (key-only).
 */
@IgnoreExtraProperties
public class MessageModel {

    // ── Message type constants ─────────────────────────────────────────────
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_FILE = "file";

    // ── Firebase fields ────────────────────────────────────────────────────
    private String senderId;
    private String messageType = TYPE_TEXT; // default to text for backward compatibility
    private String message; // text content (for text messages)
    private String mediaUrl; // Cloudinary URL (for image/video/file)
    private String fileName; // Original file name (for file messages)
    private long timestamp;
    private Map<String, Boolean> deliveredTo = new HashMap<>();
    private Map<String, Boolean> readBy = new HashMap<>();

    // Reply / Forward metadata
    private String replyToMessageId;
    private String replyToText;
    private String replyToSenderId;
    private boolean forwarded;

    // ── Runtime only ───────────────────────────────────────────────────────
    @Exclude
    private String messageId;

    /**
     * Required for Firebase deserialization.
     */
    public MessageModel() {
    }

    public MessageModel(String senderId, String message, long timestamp) {
        this.senderId = senderId;
        this.message = message;
        this.timestamp = timestamp;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────
    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String v) {
        this.senderId = v;
    }

    public String getMessageType() {
        return messageType != null ? messageType : TYPE_TEXT;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String v) {
        this.message = v;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long v) {
        this.timestamp = v;
    }

    public Map<String, Boolean> getDeliveredTo() {
        return deliveredTo;
    }

    public void setDeliveredTo(Map<String, Boolean> v) {
        this.deliveredTo = v;
    }

    public Map<String, Boolean> getReadBy() {
        return readBy;
    }

    public void setReadBy(Map<String, Boolean> v) {
        this.readBy = v;
    }

    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public String getReplyToText() {
        return replyToText;
    }

    public void setReplyToText(String replyToText) {
        this.replyToText = replyToText;
    }

    public String getReplyToSenderId() {
        return replyToSenderId;
    }

    public void setReplyToSenderId(String replyToSenderId) {
        this.replyToSenderId = replyToSenderId;
    }

    public boolean isForwarded() {
        return forwarded;
    }

    public void setForwarded(boolean forwarded) {
        this.forwarded = forwarded;
    }

    @Exclude
    public String getMessageId() {
        return messageId;
    }

    @Exclude
    public void setMessageId(String v) {
        this.messageId = v;
    }

    // ── Status helpers ─────────────────────────────────────────────────────
    /**
     * Status constants for the sender's dot indicator.
     */
    public static final int STATUS_SENT = 0;   // white
    public static final int STATUS_DELIVERED = 1;   // red
    public static final int STATUS_READ = 2;   // green

    /**
     * Computes the delivery status from the perspective of the sender.
     *
     * @param otherUid the UID of the other participant
     */
    @Exclude
    public int getStatusFor(String otherUid) {
        if (readBy != null && Boolean.TRUE.equals(readBy.get(otherUid))) {
            return STATUS_READ;
        }
        if (deliveredTo != null && Boolean.TRUE.equals(deliveredTo.get(otherUid))) {
            return STATUS_DELIVERED;
        }
        return STATUS_SENT;
    }
}
