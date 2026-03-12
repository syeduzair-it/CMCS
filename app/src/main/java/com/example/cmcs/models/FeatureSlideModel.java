package com.example.cmcs.models;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class FeatureSlideModel {

    private String slideId;
    private String imageUrl;
    private String title;
    private String description;
    private long timestamp;
    private String uploadedBy;

    public FeatureSlideModel() {
    }

    public FeatureSlideModel(String imageUrl, String title, String description, long timestamp, String uploadedBy) {
        this.imageUrl = imageUrl;
        this.title = title;
        this.description = description;
        this.timestamp = timestamp;
        this.uploadedBy = uploadedBy;
    }

    public String getSlideId() {
        return slideId;
    }

    public void setSlideId(String slideId) {
        this.slideId = slideId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}
