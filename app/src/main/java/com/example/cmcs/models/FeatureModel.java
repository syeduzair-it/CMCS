package com.example.cmcs.models;

import com.google.firebase.database.IgnoreExtraProperties;
import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class FeatureModel {

    private String featureId;
    private String title;
    private String createdBy;
    private long timestamp;
    private Map<String, FeatureSlideModel> slides = new HashMap<>();

    public FeatureModel() {
    }

    public FeatureModel(String title, String createdBy, long timestamp) {
        this.title = title;
        this.createdBy = createdBy;
        this.timestamp = timestamp;
    }

    public String getFeatureId() {
        return featureId;
    }

    public void setFeatureId(String featureId) {
        this.featureId = featureId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, FeatureSlideModel> getSlides() {
        return slides;
    }

    public void setSlides(Map<String, FeatureSlideModel> slides) {
        this.slides = slides;
    }
}
