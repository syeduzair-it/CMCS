package com.example.cmcs.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.cmcs.AddNoticeActivity;
import com.example.cmcs.R;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Host fragment for the Notice board.
 *
 * Tabs: CLASS | College | T & P
 *
 * Role rules: student → FAB hidden; CLASS tab loads
 * notices/class/<dept>/<course>/<year>
 * teacher → FAB visible; FAB routes to picker / AddNoticeActivity per tab
 */
public class NoticeFragment extends Fragment {

    private static final String[] TAB_TITLES = {"CLASS", "College", "T & P"};

    // tab indices
    private static final int TAB_CLASS = 0;
    private static final int TAB_COLLEGE = 1;
    private static final int TAB_TRAINING = 2;

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private FloatingActionButton fab;

    // Current user data (loaded from Firebase)
    private String currentUid;
    private String currentRole;
    private String currentName;
    private String currentDept;
    private String currentCourse;
    private String currentYear;

    // ── Tab badge listeners ───────────────────────────────────────────────
    private final List<DatabaseReference>  tabBadgeRefs      = new ArrayList<>();
    private final List<ValueEventListener> tabBadgeListeners = new ArrayList<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewPager = view.findViewById(R.id.noticeViewPager);
        tabLayout = view.findViewById(R.id.noticeTabLayout);
        fab = view.findViewById(R.id.fabAddNotice);

        // Hide MainActivity FAB — this fragment has its own
        hideMainActivityFab();

        // Hide fragment FAB until user role loaded
        fab.hide();

        currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUid == null) {
            return;
        }

        // Load user profile then set up UI
        FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isAdded()) {
                            return;
                        }
                        currentRole   = snapshot.child("role").getValue(String.class);
                        currentName   = snapshot.child("name").getValue(String.class);
                        currentDept   = snapshot.child("department").getValue(String.class);
                        currentCourse = snapshot.child("course").getValue(String.class);
                        currentYear   = snapshot.child("year").getValue(String.class);

                        Log.d("NOTICE_BADGE", "Profile loaded — role: " + currentRole);

                        setupViewPager();
                        setupFab();
                        setupTabBadges();

                        // Stamp immediately on first open so the badge clears right away.
                        // onResume() may have already fired before this callback returned,
                        // so we stamp here as well to guarantee it runs with a known role.
                        if ("teacher".equalsIgnoreCase(currentRole)) {
                            stampAndClearBadge();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        hideMainActivityFab();
        // Teacher: stamp last-seen time and clear the bottom nav badge.
        // currentRole may be null here on first open (async profile load not done yet),
        // so we also stamp inside the profile callback above as a guarantee.
        if ("teacher".equalsIgnoreCase(currentRole)) {
            Log.d("NOTICE_BADGE", "onResume — role is teacher, stamping");
            stampAndClearBadge();
        } else {
            Log.d("NOTICE_BADGE", "onResume — role: " + currentRole + " (no stamp)");
        }
    }

    /** Writes current time to SharedPreferences and removes the bottom-nav badge. */
    private void stampAndClearBadge() {
        if (getContext() == null) return;
        long now = System.currentTimeMillis();
        Log.d("NOTICE_BADGE", "stampAndClearBadge — writing lastSeen=" + now);
        requireContext()
                .getSharedPreferences("cmcs_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putLong("teacher_last_notice_seen", now)
                .apply();
        if (getActivity() instanceof com.example.cmcs.MainActivity) {
            ((com.example.cmcs.MainActivity) getActivity()).stampTeacherNoticeSeen();
        }
    }

    /**
     * Hide MainActivity's Action FAB since NoticeFragment has its own.
     */
    private void hideMainActivityFab() {
        if (getActivity() instanceof com.example.cmcs.MainActivity) {
            ((com.example.cmcs.MainActivity) getActivity()).hideActionFab();
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────
    private void setupViewPager() {
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return buildTabFragment(position);
            }

            @Override
            public int getItemCount() {
                return TAB_TITLES.length;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, pos) -> tab.setText(TAB_TITLES[pos])).attach();
    }

    private Fragment buildTabFragment(int position) {
        switch (position) {
            case TAB_CLASS:
                // CLASS tab is fully managed by ClassNoticeFragment
                return ClassNoticeFragment.newInstance(
                        currentRole, currentUid, currentDept,
                        currentCourse, currentYear, currentName);
            case TAB_COLLEGE:
                return NoticeListFragment.newInstance("notices/college", currentRole, currentUid, currentName);
            default: // TAB_TRAINING
                return NoticeListFragment.newInstance("notices/training", currentRole, currentUid, currentName);
        }
    }

    private void setupFab() {
        // CLASS tab owns its own FAB; parent FAB only serves College + Training for teachers.
        if ("teacher".equalsIgnoreCase(currentRole)) {
            // Set initial FAB state based on the current tab
            updateParentFab(viewPager.getCurrentItem());
            fab.setOnClickListener(v -> onFabClick());
        } else {
            fab.hide();
        }

        // Page-change callback: hide parent FAB on CLASS tab, show on others (teacher only)
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if ("teacher".equalsIgnoreCase(currentRole)) {
                    updateParentFab(position);
                }
            }
        });
    }

    /**
     * Show or hide the parent FAB depending on which tab is active.
     */
    private void updateParentFab(int position) {
        if (position == TAB_CLASS) {
            fab.hide(); // CLASS tab has its own FAB inside ClassNoticeFragment
        } else {
            fab.show();
        }
    }

    private void onFabClick() {
        int tab = viewPager.getCurrentItem();
        if (tab == TAB_COLLEGE) {
            openAddNotice("college", null, null, null);
        } else if (tab == TAB_TRAINING) {
            openAddNotice("training", null, null, null);
        }
        // TAB_CLASS is intentionally not handled here
    }

    private void openAddNotice(String mode, String dept, String course, String year) {
        Intent i = new Intent(requireContext(), AddNoticeActivity.class);
        i.putExtra(AddNoticeActivity.EXTRA_MODE, mode);
        i.putExtra(AddNoticeActivity.EXTRA_DEPARTMENT, dept != null ? dept : currentDept);
        i.putExtra(AddNoticeActivity.EXTRA_COURSE, course != null ? course : "");
        i.putExtra(AddNoticeActivity.EXTRA_YEAR, year != null ? year : "");
        i.putExtra(AddNoticeActivity.EXTRA_CREATOR_UID, currentUid);
        i.putExtra(AddNoticeActivity.EXTRA_CREATOR_NAME, currentName);
        startActivity(i);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        for (int i = 0; i < tabBadgeRefs.size(); i++) {
            tabBadgeRefs.get(i).removeEventListener(tabBadgeListeners.get(i));
        }
        tabBadgeRefs.clear();
        tabBadgeListeners.clear();
    }

    // ── Tab badges ────────────────────────────────────────────────────────

    /**
     * Attaches listeners on each tab's notice path + noticeViews.
     * Recomputes unread count per tab on every change and updates the
     * TabLayout badge accordingly.
     */
    private void setupTabBadges() {
        if (currentUid == null) return;

        // CLASS tab path
        final String classPath;
        if ("student".equalsIgnoreCase(currentRole)
                && currentDept != null && currentCourse != null && currentYear != null) {
            classPath = "notices/class/"
                    + sanitize(currentDept) + "/"
                    + normalizeCourse(currentCourse) + "/"
                    + normalizeYear(currentYear);
        } else if ("teacher".equalsIgnoreCase(currentRole) && currentDept != null) {
            classPath = "notices/class/" + sanitize(currentDept);
        } else {
            classPath = null;
        }

        watchTabPath(classPath,    TAB_CLASS);
        watchTabPath("notices/college",  TAB_COLLEGE);
        watchTabPath("notices/training", TAB_TRAINING);

        // Students also re-check when noticeViews changes (a notice gets marked read)
        if (!"teacher".equalsIgnoreCase(currentRole)) {
            DatabaseReference viewsRef = FirebaseDatabase.getInstance().getReference("noticeViews");
            ValueEventListener viewsListener = new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot s) {
                    if (classPath != null) recomputeTabBadge(classPath, TAB_CLASS);
                    recomputeTabBadge("notices/college",  TAB_COLLEGE);
                    recomputeTabBadge("notices/training", TAB_TRAINING);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            };
            tabBadgeRefs.add(viewsRef);
            tabBadgeListeners.add(viewsListener);
            viewsRef.addValueEventListener(viewsListener);
        }
    }

    private void watchTabPath(@Nullable String path, int tabIndex) {
        if (path == null) {
            setTabBadge(tabIndex, false);
            return;
        }
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                recomputeTabBadge(path, tabIndex);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                setTabBadge(tabIndex, false);
            }
        };
        tabBadgeRefs.add(ref);
        tabBadgeListeners.add(listener);
        ref.addValueEventListener(listener);
    }

    private void recomputeTabBadge(String path, int tabIndex) {
        if ("teacher".equalsIgnoreCase(currentRole)) {
            recomputeTabBadgeForTeacher(path, tabIndex);
        } else {
            recomputeTabBadgeForStudent(path, tabIndex);
        }
    }

    /** Teacher tab badge: timestamp vs SharedPreferences last-seen. */
    private void recomputeTabBadgeForTeacher(String path, int tabIndex) {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("cmcs_prefs", android.content.Context.MODE_PRIVATE);
        final long lastSeen = prefs.getLong("teacher_last_notice_seen", 0L);

        FirebaseDatabase.getInstance().getReference(path)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean hasUnread = false;
                        for (String id : extractNoticeIds(snapshot)) {
                            Long ts = getTimestampFromSnapshot(snapshot, id);
                            if (ts != null && ts > lastSeen) { hasUnread = true; break; }
                        }
                        setTabBadge(tabIndex, hasUnread);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        setTabBadge(tabIndex, false);
                    }
                });
    }

    /** Student tab badge: Firebase noticeViews check. */
    private void recomputeTabBadgeForStudent(String path, int tabIndex) {
        FirebaseDatabase.getInstance().getReference(path)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> ids = extractNoticeIds(snapshot);
                        if (ids.isEmpty()) { setTabBadge(tabIndex, false); return; }

                        AtomicInteger pending = new AtomicInteger(ids.size());
                        AtomicInteger unread  = new AtomicInteger(0);
                        for (String noticeId : ids) {
                            FirebaseDatabase.getInstance()
                                    .getReference("noticeViews")
                                    .child(noticeId).child(currentUid)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override public void onDataChange(@NonNull DataSnapshot s) {
                                            if (!s.exists()) unread.incrementAndGet();
                                            if (pending.decrementAndGet() == 0)
                                                setTabBadge(tabIndex, unread.get() > 0);
                                        }
                                        @Override public void onCancelled(@NonNull DatabaseError e) {
                                            if (pending.decrementAndGet() == 0)
                                                setTabBadge(tabIndex, unread.get() > 0);
                                        }
                                    });
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        setTabBadge(tabIndex, false);
                    }
                });
    }

    private void setTabBadge(int tabIndex, boolean show) {
        if (!isAdded() || tabLayout == null) return;
        TabLayout.Tab tab = tabLayout.getTabAt(tabIndex);
        if (tab == null) return;
        if (show) {
            BadgeDrawable badge = tab.getOrCreateBadge();
            badge.clearNumber();
            badge.setVisible(true);
        } else {
            tab.removeBadge();
        }
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

    /** Extracts push-key notice IDs from a snapshot (flat or nested class tree). */
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

    // ── Util ──────────────────────────────────────────────────────────────
    /**
     * Replace spaces and illegal Firebase key chars with underscores.
     */
    private static String sanitize(String s) {
        if (s == null) {
            return "_";
        }
        return s.trim().replace(" ", "_").replace(".", "_")
                .replace("#", "_").replace("$", "_")
                .replace("[", "_").replace("]", "_");
    }
}
