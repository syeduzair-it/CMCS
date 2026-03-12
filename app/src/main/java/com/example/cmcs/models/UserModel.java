package com.example.cmcs.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Maps to the Firebase node: users/<authUid>
 *
 * Fields stored in Firebase: name, role, department, course, year, gender,
 * profileImage
 *
 * authUid is a transient runtime field — populated from the DataSnapshot key,
 * never written back to Firebase.
 */
@IgnoreExtraProperties
public class UserModel {

    // ── Firebase-persisted fields ──────────────────────────────────────────
    private String name;
    private String role;           // "student" | "teacher"
    private String department;
    private String course;         // students only
    private String year;           // students only  (e.g. "1", "2", "3")
    private String gender;         // "male" | "female"
    private String profileImage;   // download URL

    // ── Runtime-only field ────────────────────────────────────────────────
    @Exclude
    private String authUid;        // set from DataSnapshot.getKey()

    // Required empty constructor for Firebase deserialization
    public UserModel() {
    }

    // ── Getters / Setters ─────────────────────────────────────────────────
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    @Exclude
    public String getAuthUid() {
        return authUid;
    }

    @Exclude
    public void setAuthUid(String uid) {
        this.authUid = uid;
    }

    /**
     * Human-readable subtitle shown in the user list row. Teacher → "Teacher ·
     * {department}" Student → "Student · {course} · Year {year}"
     */
    @Exclude
    public String getSubtitle() {
        if ("teacher".equalsIgnoreCase(role)) {
            return "Teacher" + (department != null ? " · " + department : "");
        }
        StringBuilder sb = new StringBuilder("Student");
        if (course != null && !course.isEmpty()) {
            sb.append(" · ").append(course);
        }
        if (year != null && !year.isEmpty()) {
            sb.append(" · Year ").append(year);
        }
        return sb.toString();
    }
}
