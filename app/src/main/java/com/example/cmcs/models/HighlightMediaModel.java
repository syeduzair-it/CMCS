package com.example.cmcs.models;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * A single media item inside a highlight, stored at:
 * highlights/<highlightId>/media/<mediaId>
 */
@IgnoreExtraProperties
public class HighlightMediaModel implements Parcelable {

    private String mediaId;     // local only
    private String mediaUrl;
    private String mediaType;   // "image" | "video"
    private long timestamp;

    public HighlightMediaModel() {
    }

    public HighlightMediaModel(String mediaUrl, String mediaType, long timestamp) {
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.timestamp = timestamp;
    }

    // ── Parcelable ────────────────────────────────────────────────────────
    protected HighlightMediaModel(Parcel in) {
        mediaId = in.readString();
        mediaUrl = in.readString();
        mediaType = in.readString();
        timestamp = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mediaId);
        dest.writeString(mediaUrl);
        dest.writeString(mediaType);
        dest.writeLong(timestamp);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<HighlightMediaModel> CREATOR = new Creator<HighlightMediaModel>() {
        @Override
        public HighlightMediaModel createFromParcel(Parcel in) {
            return new HighlightMediaModel(in);
        }

        @Override
        public HighlightMediaModel[] newArray(int size) {
            return new HighlightMediaModel[size];
        }
    };

    // ── Getters / Setters ─────────────────────────────────────────────────
    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String v) {
        mediaId = v;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long v) {
        timestamp = v;
    }
}
