package com.example.cmcs.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Represents a notice stored in Firebase Realtime Database.
 *
 * DB paths: notices/class/<department>/<course>/<year>/<noticeId>
 * notices/college/<noticeId>
 * notices/training/<noticeId>
 *
 * Storage path: notice_media/<noticeId>/file.<ext>
 */
@IgnoreExtraProperties
public class NoticeModel {

    // ── Identity ──────────────────────────────────────────────────────────
    private String noticeId;
    private String title;
    private String description;

    // ── Media ─────────────────────────────────────────────────────────────
    /**
     * "text" | "image" | "pdf" | "video"
     */
    private String mediaType;
    private String mediaUrl;

    // ── Ownership ─────────────────────────────────────────────────────────
    private String createdByUid;
    private String createdByName;

    // ── Classification ────────────────────────────────────────────────────
    /**
     * Always stored — even for college/training notices — for future filtering.
     */
    private String department;
    private String course;   // class notices only
    private String year;     // class notices only

    // ── Meta ──────────────────────────────────────────────────────────────
    private long timestamp;
    private boolean edited;

    // Required empty constructor for Firebase deserialization
    public NoticeModel() {
    }

    // ── Full constructor ──────────────────────────────────────────────────
    public NoticeModel(String noticeId, String title, String description,
            String mediaType, String mediaUrl,
            String createdByUid, String createdByName,
            String department, String course, String year,
            long timestamp, boolean edited) {
        this.noticeId = noticeId;
        this.title = title;
        this.description = description;
        this.mediaType = mediaType;
        this.mediaUrl = mediaUrl;
        this.createdByUid = createdByUid;
        this.createdByName = createdByName;
        this.department = department;
        this.course = course;
        this.year = year;
        this.timestamp = timestamp;
        this.edited = edited;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────
    public String getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(String v) {
        this.noticeId = v;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String v) {
        this.title = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String v) {
        this.mediaType = v;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String v) {
        this.mediaUrl = v;
    }

    public String getCreatedByUid() {
        return createdByUid;
    }

    public void setCreatedByUid(String v) {
        this.createdByUid = v;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String v) {
        this.createdByName = v;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String v) {
        this.department = v;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String v) {
        this.course = v;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String v) {
        this.year = v;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long v) {
        this.timestamp = v;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean v) {
        this.edited = v;
    }

    /**
     * Extension extracted from mediaUrl for Storage reference deletion.
     */
    @Exclude
    public String getStorageExtension() {
        if (mediaUrl == null || mediaUrl.isEmpty()) {
            return "";
        }
        if (mediaType == null) {
            return "";
        }
        switch (mediaType) {
            case "image":
                return "jpg";
            case "pdf":
                return "pdf";
            case "video":
                return "mp4";
            default:
                return "";
        }
    }
}
