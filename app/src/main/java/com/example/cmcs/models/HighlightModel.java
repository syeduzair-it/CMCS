package com.example.cmcs.models;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Represents a highlight album stored at: highlights/<highlightId>
 */
@IgnoreExtraProperties
public class HighlightModel {

    private String highlightId;        // local only
    private String title;
    private String createdByUid;
    private String createdByName;
    private long timestamp;
    private String coverUrl;           // URL of the first media item (set locally)

    public HighlightModel() {
    }

    public HighlightModel(String title, String createdByUid,
            String createdByName, long timestamp) {
        this.title = title;
        this.createdByUid = createdByUid;
        this.createdByName = createdByName;
        this.timestamp = timestamp;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────
    public String getHighlightId() {
        return highlightId;
    }

    public void setHighlightId(String v) {
        highlightId = v;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String v) {
        title = v;
    }

    public String getCreatedByUid() {
        return createdByUid;
    }

    public void setCreatedByUid(String v) {
        createdByUid = v;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String v) {
        createdByName = v;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long v) {
        timestamp = v;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String v) {
        coverUrl = v;
    }
}
