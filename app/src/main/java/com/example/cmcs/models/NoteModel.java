package com.example.cmcs.models;

/**
 * Represents a single note under:
 * notes/<dept>/<course>/<year>/<subject>/<noteId>
 */
public class NoteModel {

    private String noteId;
    private String title;
    private String fileUrl;
    private String fileType;        // "pdf" | "image" | "video"
    private String uploadedByUid;
    private String uploadedByName;
    private long timestamp;

    /**
     * Required no-arg constructor for Firebase deserialization.
     */
    public NoteModel() {
    }

    public NoteModel(String noteId, String title, String fileUrl, String fileType,
            String uploadedByUid, String uploadedByName, long timestamp) {
        this.noteId = noteId;
        this.title = title;
        this.fileUrl = fileUrl;
        this.fileType = fileType;
        this.uploadedByUid = uploadedByUid;
        this.uploadedByName = uploadedByName;
        this.timestamp = timestamp;
    }

    public String getNoteId() {
        return noteId;
    }

    public String getTitle() {
        return title;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public String getFileType() {
        return fileType;
    }

    public String getUploadedByUid() {
        return uploadedByUid;
    }

    public String getUploadedByName() {
        return uploadedByName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public void setUploadedByUid(String uploadedByUid) {
        this.uploadedByUid = uploadedByUid;
    }

    public void setUploadedByName(String uploadedByName) {
        this.uploadedByName = uploadedByName;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
