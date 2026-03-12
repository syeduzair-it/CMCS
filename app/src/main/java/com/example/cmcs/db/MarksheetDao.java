package com.example.cmcs.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/**
 * DAO for marksheet local storage.
 */
@Dao
public interface MarksheetDao {

    /**
     * Insert or replace an entry for a semester.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMarksheet(MarksheetEntity entity);

    /**
     * Fetch the stored marksheet for a specific semester (null if none).
     */
    @Query("SELECT * FROM marksheets WHERE semesterNumber = :semester LIMIT 1")
    MarksheetEntity getMarksheetForSemester(int semester);

    /**
     * Remove the stored record for a specific semester.
     */
    @Query("DELETE FROM marksheets WHERE semesterNumber = :semester")
    void deleteMarksheetForSemester(int semester);
}
