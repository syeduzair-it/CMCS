package com.example.cmcs.models;

import androidx.annotation.Keep;

@Keep
public class ViewerModel {

    private String uid;
    private String name;
    private String profileImage;
    private long   timestamp;
    private String role;

    public ViewerModel() {}

    /** Used by NoticeViewersActivity — no timestamp needed. */
    public ViewerModel(String uid, String name, String profileImage) {
        this.uid          = uid;
        this.name         = name;
        this.profileImage = profileImage;
    }

    /** Used by StoryViewerActivity — timestamp is meaningful for stories. */
    public ViewerModel(String uid, String name, String profileImage, long timestamp) {
        this(uid, name, profileImage);
        this.timestamp = timestamp;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
