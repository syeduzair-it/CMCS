package com.example.cmcs.models;

import com.google.firebase.database.Exclude;
import java.util.HashMap;
import java.util.Map;

public class PostModel {

    private String postId;
    private String teacherUid;
    private String teacherName;
    private String teacherProfileImage;
    private String mediaUrl;
    private String mediaType; // "image" or "video"
    private String caption;
    private long timestamp;
    private Map<String, Boolean> likes = new HashMap<>();

    public PostModel() {
    }

    public PostModel(String teacherUid, String teacherName, String teacherProfileImage,
            String mediaUrl, String mediaType, String caption, long timestamp) {
        this.teacherUid = teacherUid;
        this.teacherName = teacherName;
        this.teacherProfileImage = teacherProfileImage;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.caption = caption;
        this.timestamp = timestamp;
    }

    @Exclude
    public String getPostId() {
        return postId;
    }

    @Exclude
    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getTeacherUid() {
        return teacherUid;
    }

    public void setTeacherUid(String teacherUid) {
        this.teacherUid = teacherUid;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public String getTeacherProfileImage() {
        return teacherProfileImage;
    }

    public void setTeacherProfileImage(String teacherProfileImage) {
        this.teacherProfileImage = teacherProfileImage;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Boolean> getLikes() {
        return likes;
    }

    public void setLikes(Map<String, Boolean> likes) {
        this.likes = likes;
    }
}
