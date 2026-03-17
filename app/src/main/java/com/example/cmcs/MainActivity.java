package com.example.cmcs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.cmcs.fragments.ChatsFragment;
import com.example.cmcs.fragments.HomeFragment;
import com.example.cmcs.fragments.MeFragment;
import com.example.cmcs.fragments.NoticeFragment;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private TextView              tvToolbarTitle;
    private ImageView             ivMenu;
    private BottomNavigationView  bottomNavView;
    private FloatingActionButton  fabScanner;
    private FloatingActionButton  fabAction;
    private String                userRole     = "student";
    private Fragment              currentFragment;

    // ── User profile (loaded once) ────────────────────────────────────────
    private String currentUid;
    private String currentDept;
    private String currentCourse;
    private String currentYear;
    private boolean profileLoaded = false;

    // ── Badge listeners (detached in onStop) ─────────────────────────────
    private ValueEventListener chatBadgeListener;
    private ValueEventListener notesBadgeListener;

    private DatabaseReference chatBadgeRef;
    private DatabaseReference notesBadgeRef;

    // ── Notice unread tracking ────────────────────────────────────────────
    // We keep a list of active per-notice view-check listeners so we can
    // detach them all in onStop without leaking.
    private final List<DatabaseReference> noticeViewRefs   = new ArrayList<>();
    private final List<ValueEventListener> noticeViewListeners = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        ivMenu        = findViewById(R.id.ivMenu);
        bottomNavView = findViewById(R.id.bottomNavView);
        fabScanner    = findViewById(R.id.fabScanner);
        fabAction     = findViewById(R.id.fabAction);

        ivMenu.setOnClickListener(v -> showToolbarMenu(v));

        loadFragment(new HomeFragment(), "CMCS");
        bottomNavView.setSelectedItemId(R.id.nav_home);

        bottomNavView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment f = null;
            String title = "CMCS";

            if (id == R.id.nav_home) {
                f = new HomeFragment(); title = "CMCS";
            } else if (id == R.id.nav_chats) {
                f = new ChatsFragment(); title = "Chats";
            } else if (id == R.id.nav_notice) {
                f = new NoticeFragment(); title = "Notices";
            } else if (id == R.id.nav_me) {
                f = new MeFragment(); title = "Profile";
            }

            if (f != null) { loadFragment(f, title); return true; }
            return false;
        });

        fabAction.hide();

        // Load user profile, then start badge listeners
        loadUserProfile();

        fabScanner.setOnClickListener(v -> {
            Intent intent = "teacher".equalsIgnoreCase(userRole)
                    ? new Intent(this, ClassSelectionActivity.class)
                    : new Intent(this, ScannerActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Re-attach badge listeners when activity comes to foreground
        if (profileLoaded) startBadgeListeners();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopBadgeListeners();
    }

    // ── User profile ──────────────────────────────────────────────────────

    private void loadUserProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        currentUid = user.getUid();

        FirebaseDatabase.getInstance().getReference("users").child(currentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        userRole      = snapshot.child("role").getValue(String.class);
                        currentDept   = snapshot.child("department").getValue(String.class);
                        currentCourse = snapshot.child("course").getValue(String.class);
                        currentYear   = snapshot.child("year").getValue(String.class);
                        if (userRole == null) userRole = "student";
                        profileLoaded = true;
                        startBadgeListeners();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ── Badge listeners ───────────────────────────────────────────────────

    private void startBadgeListeners() {
        if (currentUid == null) return;
        startChatBadgeListener();
        startNoticeBadgeListener();
        if ("student".equalsIgnoreCase(userRole)) {
            startNotesBadgeListener();
        }
    }

    private void stopBadgeListeners() {
        if (chatBadgeRef != null && chatBadgeListener != null) {
            chatBadgeRef.removeEventListener(chatBadgeListener);
        }
        if (notesBadgeRef != null && notesBadgeListener != null) {
            notesBadgeRef.removeEventListener(notesBadgeListener);
        }
        // Detach all notice path listeners
        for (int i = 0; i < noticeViewRefs.size(); i++) {
            noticeViewRefs.get(i).removeEventListener(noticeViewListeners.get(i));
        }
        noticeViewRefs.clear();
        noticeViewListeners.clear();
    }

    // ── Chat badge ────────────────────────────────────────────────────────

    /**
     * Listens to userChats/{uid} in real time.
     * Sums all unreadCount values and shows a numbered badge on nav_chats.
     */
    private void startChatBadgeListener() {
        chatBadgeRef = FirebaseDatabase.getInstance()
                .getReference("userChats").child(currentUid);

        chatBadgeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int total = 0;
                for (DataSnapshot chat : snapshot.getChildren()) {
                    Long unread = chat.child("unreadCount").getValue(Long.class);
                    if (unread != null) total += unread.intValue();
                }
                updateChatBadge(total);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };

        chatBadgeRef.addValueEventListener(chatBadgeListener);
    }

    private void updateChatBadge(int count) {
        if (count > 0) {
            BadgeDrawable badge = bottomNavView.getOrCreateBadge(R.id.nav_chats);
            badge.setNumber(count);
            badge.setVisible(true);
        } else {
            bottomNavView.removeBadge(R.id.nav_chats);
        }
    }

    // ── Notice badge ──────────────────────────────────────────────────────

    private static final String PREFS_NAME              = "cmcs_prefs";
    private static final String KEY_TEACHER_NOTICE_SEEN = "teacher_last_notice_seen";

    /**
     * STUDENT: checks noticeViews/{noticeId}/{uid} — Firebase-driven.
     * TEACHER: compares notice.timestamp against locally stored last-seen time.
     *          No reads/writes to noticeViews for teachers.
     */
    private void startNoticeBadgeListener() {
        List<String> paths = new ArrayList<>();
        paths.add("notices/college");
        paths.add("notices/training");

        if ("student".equalsIgnoreCase(userRole)
                && currentDept != null && currentCourse != null && currentYear != null) {
            paths.add("notices/class/"
                    + sanitize(currentDept) + "/"
                    + normalizeCourse(currentCourse) + "/"
                    + normalizeYear(currentYear));
        } else if ("teacher".equalsIgnoreCase(userRole) && currentDept != null) {
            paths.add("notices/class/" + sanitize(currentDept));
        }

        for (String path : paths) {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);
            ValueEventListener listener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    recomputeNoticeBadge();
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            };
            noticeViewRefs.add(ref);
            noticeViewListeners.add(listener);
            ref.addValueEventListener(listener);
        }

        // Students also react when noticeViews changes (a notice gets marked read)
        if ("student".equalsIgnoreCase(userRole)) {
            DatabaseReference viewsRef = FirebaseDatabase.getInstance().getReference("noticeViews");
            ValueEventListener viewsListener = new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                    recomputeNoticeBadge();
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            };
            noticeViewRefs.add(viewsRef);
            noticeViewListeners.add(viewsListener);
            viewsRef.addValueEventListener(viewsListener);
        }
    }

    private void recomputeNoticeBadge() {
        if (currentUid == null) return;
        if ("teacher".equalsIgnoreCase(userRole)) {
            recomputeNoticeBadgeForTeacher();
        } else {
            recomputeNoticeBadgeForStudent();
        }
    }

    /** Teacher badge: timestamp vs SharedPreferences last-seen. No noticeViews reads. */
    private void recomputeNoticeBadgeForTeacher() {
        final long lastSeen = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getLong(KEY_TEACHER_NOTICE_SEEN, 0L);

        Log.d("NOTICE_BADGE", "recomputeForTeacher — lastSeen=" + lastSeen);

        List<String> paths = new ArrayList<>();
        paths.add("notices/college");
        paths.add("notices/training");
        if (currentDept != null) paths.add("notices/class/" + sanitize(currentDept));

        int totalPaths = paths.size();
        AtomicInteger pathsDone   = new AtomicInteger(0);
        AtomicInteger unreadTotal = new AtomicInteger(0);

        for (String path : paths) {
            FirebaseDatabase.getInstance().getReference(path)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                            for (String id : extractNoticeIds(snapshot)) {
                                Long ts = getTimestampFromSnapshot(snapshot, id);
                                Log.d("NOTICE_BADGE", "  notice=" + id + " ts=" + ts + " lastSeen=" + lastSeen + " unread=" + (ts != null && ts > lastSeen));
                                if (ts != null && ts > lastSeen) unreadTotal.incrementAndGet();
                            }
                            if (pathsDone.incrementAndGet() >= totalPaths) {
                                Log.d("NOTICE_BADGE", "recomputeForTeacher — unreadTotal=" + unreadTotal.get());
                                updateNoticeBadge(unreadTotal.get());
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {
                            if (pathsDone.incrementAndGet() >= totalPaths)
                                updateNoticeBadge(unreadTotal.get());
                        }
                    });
        }
    }

    /** Student badge: Firebase noticeViews/{id}/{uid} check. */
    private void recomputeNoticeBadgeForStudent() {
        List<String> paths = new ArrayList<>();
        paths.add("notices/college");
        paths.add("notices/training");
        if (currentDept != null && currentCourse != null && currentYear != null) {
            paths.add("notices/class/"
                    + sanitize(currentDept) + "/"
                    + normalizeCourse(currentCourse) + "/"
                    + normalizeYear(currentYear));
        }

        int totalPaths = paths.size();
        AtomicInteger pathsDone   = new AtomicInteger(0);
        AtomicInteger unreadTotal = new AtomicInteger(0);

        for (String path : paths) {
            FirebaseDatabase.getInstance().getReference(path)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                            List<String> ids = extractNoticeIds(snapshot);
                            if (ids.isEmpty()) {
                                if (pathsDone.incrementAndGet() >= totalPaths)
                                    updateNoticeBadge(unreadTotal.get());
                                return;
                            }
                            AtomicInteger pending = new AtomicInteger(ids.size());
                            for (String noticeId : ids) {
                                FirebaseDatabase.getInstance()
                                        .getReference("noticeViews")
                                        .child(noticeId).child(currentUid)
                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override public void onDataChange(@NonNull DataSnapshot s) {
                                                if (!s.exists()) unreadTotal.incrementAndGet();
                                                if (pending.decrementAndGet() == 0
                                                        && pathsDone.incrementAndGet() >= totalPaths)
                                                    updateNoticeBadge(unreadTotal.get());
                                            }
                                            @Override public void onCancelled(@NonNull DatabaseError e) {
                                                if (pending.decrementAndGet() == 0
                                                        && pathsDone.incrementAndGet() >= totalPaths)
                                                    updateNoticeBadge(unreadTotal.get());
                                            }
                                        });
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {
                            if (pathsDone.incrementAndGet() >= totalPaths)
                                updateNoticeBadge(unreadTotal.get());
                        }
                    });
        }
    }

    private static List<String> extractNoticeIds(DataSnapshot snapshot) {
        List<String> ids = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            if (child.hasChild("timestamp")) {
                String key = child.getKey();
                if (key != null && key.startsWith("-")) ids.add(key);
            } else {
                for (DataSnapshot lvl2 : child.getChildren()) {
                    for (DataSnapshot lvl3 : lvl2.getChildren()) {
                        for (DataSnapshot notice : lvl3.getChildren()) {
                            String key = notice.getKey();
                            if (key != null && key.startsWith("-")) ids.add(key);
                        }
                    }
                }
            }
        }
        return ids;
    }

    /** Finds a notice's timestamp within an already-fetched snapshot — no extra read. */
    private static Long getTimestampFromSnapshot(DataSnapshot snapshot, String noticeId) {
        DataSnapshot direct = snapshot.child(noticeId);
        if (direct.exists()) return direct.child("timestamp").getValue(Long.class);
        for (DataSnapshot lvl1 : snapshot.getChildren()) {
            for (DataSnapshot lvl2 : lvl1.getChildren()) {
                for (DataSnapshot lvl3 : lvl2.getChildren()) {
                    if (noticeId.equals(lvl3.getKey()))
                        return lvl3.child("timestamp").getValue(Long.class);
                }
            }
        }
        return null;
    }

    private void updateNoticeBadge(int unreadCount) {
        if (unreadCount > 0) {
            BadgeDrawable badge = bottomNavView.getOrCreateBadge(R.id.nav_notice);
            badge.clearNumber();
            badge.setVisible(true);
        } else {
            bottomNavView.removeBadge(R.id.nav_notice);
        }
    }

    /**
     * Called by NoticeFragment when a teacher opens the notice screen.
     * Stamps current time as last-seen, clears the badge immediately,
     * then re-runs the badge computation so it reflects the new timestamp.
     */
    public void stampTeacherNoticeSeen() {
        long now = System.currentTimeMillis();
        Log.d("NOTICE_BADGE", "stampTeacherNoticeSeen — writing lastSeen=" + now);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong(KEY_TEACHER_NOTICE_SEEN, now)
                .apply();
        // Remove badge immediately (optimistic clear)
        bottomNavView.removeBadge(R.id.nav_notice);
        // Re-run computation so any in-flight listener results also see the new timestamp
        recomputeNoticeBadge();
    }

    // ── Notes badge (student only) ────────────────────────────────────────

    /**
     * Listens to notes/{dept}/{course}/{year} for new note uploads.
     * Checks noteViews/{noteId}/{uid} to determine if the student has opened it.
     * Shows a dot badge on nav_me if any unread notes exist.
     */
    private void startNotesBadgeListener() {
        if (currentDept == null || currentCourse == null || currentYear == null) return;

        notesBadgeRef = FirebaseDatabase.getInstance()
                .getReference("notes")
                .child(sanitize(currentDept))
                .child(currentCourse != null ? currentCourse.trim().toLowerCase() : "_")
                .child(sanitize(currentYear));

        notesBadgeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> noteIds = new ArrayList<>();
                for (DataSnapshot subject : snapshot.getChildren()) {
                    for (DataSnapshot note : subject.getChildren()) {
                        String key = note.getKey();
                        if (key != null && !key.equals("_created")) noteIds.add(key);
                    }
                }
                if (noteIds.isEmpty()) {
                    updateNotesBadge(false);
                    return;
                }
                // Check noteViews/{noteId}/{uid} per note (scoped — no full-node scan)
                AtomicInteger pending = new AtomicInteger(noteIds.size());
                AtomicInteger unreadCount = new AtomicInteger(0);
                for (String noteId : noteIds) {
                    FirebaseDatabase.getInstance()
                            .getReference("noteViews").child(noteId).child(currentUid)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot s) {
                                    if (!s.exists()) unreadCount.incrementAndGet();
                                    if (pending.decrementAndGet() == 0)
                                        updateNotesBadge(unreadCount.get() > 0);
                                }
                                @Override public void onCancelled(@NonNull DatabaseError e) {
                                    if (pending.decrementAndGet() == 0)
                                        updateNotesBadge(unreadCount.get() > 0);
                                }
                            });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                updateNotesBadge(false);
            }
        };

        notesBadgeRef.addValueEventListener(notesBadgeListener);
    }

    private void updateNotesBadge(boolean hasUnread) {
        BadgeDrawable badge = bottomNavView.getOrCreateBadge(R.id.nav_me);
        if (hasUnread) {
            badge.clearNumber();
            badge.setVisible(true);
        } else {
            badge.setVisible(false);
        }
    }

    // ── Fragment loading ──────────────────────────────────────────────────

    private void loadFragment(Fragment fragment, String title) {
        currentFragment = fragment;
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();

        if (tvToolbarTitle != null) tvToolbarTitle.setText(title);

        configureActionFabForFragment(fragment);
    }

    private void configureActionFabForFragment(Fragment fragment) {
        if (fragment instanceof ChatsFragment) {
            fabAction.show();
            fabAction.setImageResource(R.drawable.ic_new_chat);
            fabAction.setContentDescription("New Chat");
            fabAction.setOnClickListener(v ->
                    startActivity(new Intent(this, SelectUserActivity.class)));
        } else if (fragment instanceof NoticeFragment) {
            fabAction.hide();
        } else {
            fabAction.hide();
        }
    }

    // ── Public API for fragments ──────────────────────────────────────────

    public void hideActionFab() {
        if (fabAction != null) fabAction.hide();
    }

    public void showActionFab(int iconRes, String description, View.OnClickListener listener) {
        if (fabAction != null) {
            fabAction.setImageResource(iconRes);
            fabAction.setContentDescription(description);
            fabAction.setOnClickListener(listener);
            fabAction.show();
        }
    }

    public String getUserRole() { return userRole; }

    /** Clear the Me/notes badge — called by NotesListActivity when notes are opened. */
    public void clearNotesBadge() {
        BadgeDrawable badge = bottomNavView.getOrCreateBadge(R.id.nav_me);
        badge.setVisible(false);
    }

    public void setDrawerLockMode(int lockMode, View drawerView) { /* no-op */ }

    // ── Toolbar popup menu ────────────────────────────────────────────────

    private void showToolbarMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.toolbar_menu);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_settings) {
                startActivityIfExists("com.example.cmcs.SettingsActivity");
            } else if (id == R.id.menu_magazine) {
                startActivityIfExists("com.example.cmcs.MagazineActivity");
            } else if (id == R.id.menu_about_cmcs) {
                startActivityIfExists("com.example.cmcs.AboutCMCSActivity");
            } else if (id == R.id.menu_about_sanstha) {
                startActivityIfExists("com.example.cmcs.AboutSansthaActivity");
            } else if (id == R.id.menu_license) {
                startActivityIfExists("com.example.cmcs.LicenseActivity");
            } else if (id == R.id.menu_about_developer) {
                startActivityIfExists("com.example.cmcs.DeveloperActivity");
            } else if (id == R.id.menu_report_bug) {
                startActivityIfExists("com.example.cmcs.BugReportActivity");
            } else if (id == R.id.menu_app_version) {
                showAppVersionDialog();
            }
            return true;
        });
        popup.show();
    }

    private void startActivityIfExists(String className) {
        try {
            startActivity(new Intent(this, Class.forName(className)));
        } catch (ClassNotFoundException e) {
            Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppVersionDialog() {
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "Unknown";
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("App Version")
                .setMessage("CMCS v" + version)
                .setPositiveButton("OK", null)
                .show();
    }

    // ── Path helpers (mirrors ClassNoticeFragment) ────────────────────────

    private static String sanitize(String s) {
        if (s == null) return "_";
        return s.trim().replace(" ", "_").replace(".", "_")
                .replace("#", "_").replace("$", "_")
                .replace("[", "_").replace("]", "_");
    }

    private static String normalizeCourse(String course) {
        if (course == null) return "_";
        return course.trim().toLowerCase();
    }

    private static String normalizeYear(String year) {
        if (year == null) return "_";
        switch (year.trim()) {
            case "1": return "1st_year";
            case "2": return "2nd_year";
            case "3": return "3rd_year";
            default:  return sanitize(year);
        }
    }
}
