package com.example.cmcs;

import android.app.Application;

import com.cloudinary.android.MediaManager;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * Application class – initialised once per process lifetime. Firebase offline
 * persistence MUST be enabled here (before any FirebaseDatabase reference is
 * created elsewhere).
 */
public class CMCSApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Enable local disk persistence so the chat list stays visible offline
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        // Initialise Cloudinary once per process.
        // The try/catch guard prevents IllegalStateException if the SDK is
        // already initialised (e.g. during instrumented tests or multi-process
        // scenarios).
        try {
            MediaManager.get(); // throws if not yet initialised
        } catch (IllegalStateException e) {
            Map<String, Object> config = new HashMap<>();
            config.put("cloud_name", "cmcsapp");
            MediaManager.init(this, config);
        }
    }
}
