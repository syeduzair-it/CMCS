package com.example.cmcs.fragments;

import android.content.Intent;
import android.os.Bundle;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

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

        // Hide FAB until user role loaded
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
                        currentRole = snapshot.child("role").getValue(String.class);
                        currentName = snapshot.child("name").getValue(String.class);
                        currentDept = snapshot.child("department").getValue(String.class);
                        currentCourse = snapshot.child("course").getValue(String.class);
                        currentYear = snapshot.child("year").getValue(String.class);

                        setupViewPager();
                        setupFab();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
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
                return NoticeListFragment.newInstance("notices/college", currentRole, currentUid);
            default: // TAB_TRAINING
                return NoticeListFragment.newInstance("notices/training", currentRole, currentUid);
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
