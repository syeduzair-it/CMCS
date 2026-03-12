package com.example.cmcs.utils;

import android.content.Context;
import android.net.Uri;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.util.Map;

/**
 * CloudinaryUploader — single-entry-point Cloudinary upload helper.
 *
 * Uses the unsigned preset "cmcs_media" on cloud "cmcsapp".
 *
 * Resource type is resolved from the file's MIME type rather than relying on
 * Cloudinary's "auto" detection, because unsigned upload presets can lock the
 * resource_type on the server side and silently ignore the client's "auto" hint
 * (causing PDFs to land under /image/upload/ instead of /raw/upload/).
 *
 * image/* → resource_type = "image" → /image/upload/ video/* → resource_type =
 * "video" → /video/upload/ everything else → resource_type = "raw" →
 * /raw/upload/
 *
 * quality = auto is applied only for image/video uploads; it is omitted for raw
 * resources where it has no effect.
 *
 * Usage: CloudinaryUploader.upload(context, uri, "notes", new
 * CloudinaryUploader.Callback() { public void onProgress(int percent) { ... }
 * public void onSuccess(String secureUrl) { ... } public void onFailure(String
 * error) { ... } });
 */
public final class CloudinaryUploader {

    private static final String UNSIGNED_PRESET = "cmcs_media";

    /**
     * Caller-facing callback — all methods run on the Cloudinary SDK's callback
     * thread.
     */
    public interface Callback {

        /**
         * Called periodically during upload. percent is 0–100.
         */
        void onProgress(int percent);

        /**
         * Called when the file is fully uploaded and the secure URL is
         * available.
         */
        void onSuccess(String secureUrl);

        /**
         * Called on any upload error with a human-readable message.
         */
        void onFailure(String error);
    }

    private CloudinaryUploader() {
        /* static utility */ }

    /**
     * Upload a URI to Cloudinary.
     *
     * @param context Android context (required by the SDK dispatcher)
     * @param uri Content URI of the file to upload
     * @param folder Destination folder in Cloudinary, e.g. "notes" or
     * "profile_images"
     * @param cb Result callback
     */
    public static void upload(Context context, Uri uri, String folder, Callback cb) {
        String resourceType = resolveResourceType(context, uri);
        boolean isMedia = "image".equals(resourceType) || "video".equals(resourceType);

        MediaManager.get()
                .upload(uri)
                .unsigned(UNSIGNED_PRESET)
                .option("resource_type", resourceType) // explicit, never "auto"
                .option("folder", folder)
                .option("quality", isMedia ? "auto" : null) // only for image/video
                .callback(new UploadCallback() {

                    @Override
                    public void onStart(String requestId) {
                        cb.onProgress(0);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        if (totalBytes > 0) {
                            int percent = (int) ((bytes * 100L) / totalBytes);
                            cb.onProgress(percent);
                        }
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        Object url = resultData.get("secure_url");
                        if (url != null) {
                            cb.onSuccess(url.toString());
                        } else {
                            cb.onFailure("Upload succeeded but no URL was returned.");
                        }
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        cb.onFailure(error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        /* SDK will retry automatically — no action needed */
                    }
                })
                .dispatch(context);
    }

    /**
     * Maps a file URI's MIME type to the correct Cloudinary resource_type
     * string.
     *
     * "image" → stored at /image/upload/ — supports JPEG, PNG, WebP, GIF, etc.
     * "video" → stored at /video/upload/ — supports MP4, AVI, MOV, etc. "raw" →
     * stored at /raw/upload/ — PDF, DOCX, and all other binary files.
     *
     * Falls back to "raw" for any unrecognised or null MIME type so that
     * documents are never misrouted through the image pipeline.
     */
    private static String resolveResourceType(Context context, Uri uri) {
        String mime = context.getContentResolver().getType(uri);
        if (mime == null) {
            return "raw";
        }
        if (mime.startsWith("image/")) {
            return "image";
        }
        if (mime.startsWith("video/")) {
            return "video";
        }
        // application/pdf, application/msword, application/zip, etc.
        return "raw";
    }
}
