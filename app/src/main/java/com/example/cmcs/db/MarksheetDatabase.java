package com.example.cmcs.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Room Database singleton — holds marksheet metadata. Actual files live in
 * getFilesDir()/marksheets/.
 */
@Database(entities = {MarksheetEntity.class}, version = 1, exportSchema = false)
public abstract class MarksheetDatabase extends RoomDatabase {

    public abstract MarksheetDao marksheetDao();

    private static volatile MarksheetDatabase INSTANCE;

    public static MarksheetDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (MarksheetDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            MarksheetDatabase.class,
                            "cmcs_marksheet_db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
