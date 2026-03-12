package com.example.cmcs.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity — one row per semester marksheet stored locally. Table:
 * marksheets
 */
@Entity(tableName = "marksheets")
public class MarksheetEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /**
     * Semester number (1-based, e.g. 1..8 for a 4-year course)
     */
    public int semesterNumber;

    /**
     * Absolute path to the copied file inside getFilesDir()/marksheets/
     */
    public String filePath;

    /**
     * Original file display name (e.g. "Marksheet_Sem1.pdf")
     */
    public String fileName;

    /**
     * Epoch-ms timestamp when the record was saved
     */
    public long uploadedAt;
}
