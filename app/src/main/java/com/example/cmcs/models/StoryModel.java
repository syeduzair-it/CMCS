package com.example.cmcs.models;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Represents a story in Firebase.
 *
 * DB paths: stories/college/<storyId>
 * stories/teachers/<teacherUid>/<storyId>
 *
 * Storage paths: story_media/college/<storyId>/file.<ext>
 * story_media/teachers/<teacherUid>/<storyId>/file.<ext>
 *
 * type = "college" | "teacher" mediaType = "image" | "video"
 */
@IgnoreExtraProperties
public class StoryModel implements Parcelable {

    private String storyId;
    private String mediaUrl;
    private String mediaType;   // "image" | "video"
    private String teacherUid;
    private String teacherName;
    private long timestamp;
    private String type;        // "college" | "teacher"

    // Required for Firebase deserialization
    public StoryModel() {
    }

    public StoryModel(String storyId, String mediaUrl, String mediaType,
            String teacherUid, String teacherName,
            long timestamp, String type) {
        this.storyId = storyId;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.teacherUid = teacherUid;
        this.teacherName = teacherName;
        this.timestamp = timestamp;
        this.type = type;
    }

    // ── Parcelable ────────────────────────────────────────────────────────
    protected StoryModel(Parcel in) {
        storyId = in.readString();
        mediaUrl = in.readString();
        mediaType = in.readString();
        teacherUid = in.readString();
        teacherName = in.readString();
        timestamp = in.readLong();
        type = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(storyId);
        dest.writeString(mediaUrl);
        dest.writeString(mediaType);
        dest.writeString(teacherUid);
        dest.writeString(teacherName);
        dest.writeLong(timestamp);
        dest.writeString(type);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<StoryModel> CREATOR = new Creator<StoryModel>() {
        @Override
        public StoryModel createFromParcel(Parcel in) {
            return new StoryModel(in);
        }

        @Override
        public StoryModel[] newArray(int size) {
            return new StoryModel[size];
        }
    };

    // ── Getters / Setters ─────────────────────────────────────────────────
    public String getStoryId() {
        return storyId;
    }

    public void setStoryId(String v) {
        storyId = v;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String v) {
        mediaUrl = v;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String v) {
        mediaType = v;
    }

    public String getTeacherUid() {
        return teacherUid;
    }

    public void setTeacherUid(String v) {
        teacherUid = v;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String v) {
        teacherName = v;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long v) {
        timestamp = v;
    }

    public String getType() {
        return type;
    }

    public void setType(String v) {
        type = v;
    }
}
