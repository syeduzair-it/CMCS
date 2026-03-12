package com.example.cmcs.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.AddNoticeActivity;
import com.example.cmcs.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles the CLASS tab of the Notice board.
 *
 * Student flow: Directly loads NoticeListFragment for
 * notices/class/<dept>/<course>/<year>. No FAB shown.
 *
 * Teacher flow: Step 1 – Shows a RecyclerView listing unique (course, year)
 * pairs derived from students in the teacher's department (single-shot read).
 * Step 2 – On class row click: shows NoticeListFragment for that class. Stores
 * selectedCourse/selectedYear (rotation-safe). Step 3 – FAB: if a class is
 * selected → open AddNoticeActivity; otherwise → Toast "Select a class first".
 * Back press while viewing class notices → returns to class list.
 *
 * Files NOT touched by this fragment: NoticeListFragment, AddNoticeActivity,
 * NoticeAdapter, DB structure.
 */
public class ClassNoticeFragment extends Fragment {

    // ── Args ──────────────────────────────────────────────────────────────
    private static final String ARG_ROLE = "role";
    private static final String ARG_UID = "uid";
    private static final String ARG_DEPT = "dept";
    private static final String ARG_COURSE = "course";   // student's own course
    private static final String ARG_YEAR = "year";     // student's own year
    private static final String ARG_NAME = "name";

    // ── Saved state keys ──────────────────────────────────────────────────
    private static final String STATE_SELECTED_COURSE = "sel_course";
    private static final String STATE_SELECTED_YEAR = "sel_year";
    private static final String STATE_SHOW_LIST = "show_list";

    // ── Fragment fields ───────────────────────────────────────────────────
    private String role;
    private String uid;
    private String dept;
    private String ownCourse;  // student
    private String ownYear;    // student
    private String name;

    // Teacher state (persisted across rotation)
    private String selectedCourse = null;
    private String selectedYear = null;
    private boolean showingList = false; // true when NoticeListFragment is visible

    // Views
    private RecyclerView rvClassList;
    private View classNoticeContainer;
    private FloatingActionButton fabClassNotice;

    // Back-press callback (teacher only)
    private OnBackPressedCallback backCallback;

    // Inner model for class list rows
    static class ClassOption {

        final String course, year;

        ClassOption(String c, String y) {
            course = c;
            year = y;
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────
    public static ClassNoticeFragment newInstance(String role, String uid,
            String dept, String course, String year, String name) {
        ClassNoticeFragment f = new ClassNoticeFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ROLE, role);
        b.putString(ARG_UID, uid);
        b.putString(ARG_DEPT, dept);
        b.putString(ARG_COURSE, course);
        b.putString(ARG_YEAR, year);
        b.putString(ARG_NAME, name);
        f.setArguments(b);
        return f;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Read args
        if (getArguments() != null) {
            role = getArguments().getString(ARG_ROLE, "");
            uid = getArguments().getString(ARG_UID, "");
            dept = getArguments().getString(ARG_DEPT, "");
            ownCourse = getArguments().getString(ARG_COURSE, "");
            ownYear = getArguments().getString(ARG_YEAR, "");
            name = getArguments().getString(ARG_NAME, "");
        }

        // Restore rotation state
        if (savedInstanceState != null) {
            selectedCourse = savedInstanceState.getString(STATE_SELECTED_COURSE);
            selectedYear = savedInstanceState.getString(STATE_SELECTED_YEAR);
            showingList = savedInstanceState.getBoolean(STATE_SHOW_LIST, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_class_notice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvClassList = view.findViewById(R.id.rvClassList);
        classNoticeContainer = view.findViewById(R.id.classNoticeContainer);
        fabClassNotice = view.findViewById(R.id.fabClassNotice);

        if (isStudent()) {
            setupStudentFlow();
        } else {
            setupTeacherFlow();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_SELECTED_COURSE, selectedCourse);
        out.putString(STATE_SELECTED_YEAR, selectedYear);
        out.putBoolean(STATE_SHOW_LIST, showingList);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove the back-press callback to avoid leaks
        if (backCallback != null) {
            backCallback.remove();
            backCallback = null;
        }
    }

    // ── Student Flow ──────────────────────────────────────────────────────
    private void setupStudentFlow() {
        // Hide class list and FAB – students go straight to notices
        rvClassList.setVisibility(View.GONE);
        fabClassNotice.setVisibility(View.GONE);

        String dbPath = "notices/class/"
                + sanitize(dept) + "/"
                + sanitize(ownCourse) + "/"
                + sanitize(ownYear);

        showNoticeList(dbPath);
    }

    // ── Teacher Flow ──────────────────────────────────────────────────────
    private void setupTeacherFlow() {
        // FAB visible for teachers
        fabClassNotice.setVisibility(View.VISIBLE);
        fabClassNotice.setOnClickListener(v -> onFabClick());

        // Register back callback (disabled until a class is selected)
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                returnToClassList();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), backCallback);

        if (showingList && selectedCourse != null) {
            // Restore state after rotation – skip straight to notice list
            rvClassList.setVisibility(View.GONE);
            classNoticeContainer.setVisibility(View.VISIBLE);
            String dbPath = buildClassPath(dept, selectedCourse, selectedYear);
            showNoticeList(dbPath);
            backCallback.setEnabled(true);
        } else {
            // Fresh open – show class list
            showClassList();
        }
    }

    /**
     * Load and display the list of unique (course, year) pairs for this dept.
     */
    private void showClassList() {
        rvClassList.setVisibility(View.VISIBLE);
        classNoticeContainer.setVisibility(View.GONE);
        backCallback.setEnabled(false);

        rvClassList.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<ClassOption> options = new ArrayList<>();
        ClassOptionAdapter adapter = new ClassOptionAdapter(options, this::onClassSelected);
        rvClassList.setAdapter(adapter);

        // Single-shot fetch: query all users in this department
        FirebaseDatabase.getInstance()
                .getReference("users")
                .orderByChild("department")
                .equalTo(dept)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isAdded()) {
                            return;
                        }

                        // Collect unique (course, year) pairs from students only
                        Set<String> seen = new LinkedHashSet<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String r = child.child("role").getValue(String.class);
                            String c = child.child("course").getValue(String.class);
                            String y = child.child("year").getValue(String.class);
                            if ("student".equalsIgnoreCase(r) && c != null && y != null) {
                                String key = c + "_" + y;
                                if (seen.add(key)) {
                                    options.add(new ClassOption(c, y));
                                }
                            }
                        }

                        // Sort: course alphabetically, then year numerically ascending
                        Collections.sort(options, (a, b) -> {
                            int cmp = a.course.compareToIgnoreCase(b.course);
                            if (cmp != 0) {
                                return cmp;
                            }
                            try {
                                return Integer.compare(
                                        Integer.parseInt(a.year),
                                        Integer.parseInt(b.year));
                            } catch (NumberFormatException e) {
                                return a.year.compareToIgnoreCase(b.year);
                            }
                        });

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Silently ignore – list stays empty
                    }
                });
    }

    /**
     * Called when the teacher taps a class row.
     */
    private void onClassSelected(ClassOption option) {
        selectedCourse = option.course;
        selectedYear = option.year;
        showingList = true;

        rvClassList.setVisibility(View.GONE);
        classNoticeContainer.setVisibility(View.VISIBLE);
        backCallback.setEnabled(true);

        String dbPath = buildClassPath(dept, selectedCourse, selectedYear);
        showNoticeList(dbPath);
    }

    /**
     * Swap back to the class list (teacher back-press or programmatic).
     */
    private void returnToClassList() {
        selectedCourse = null;
        selectedYear = null;
        showingList = false;

        // Remove the notice list child fragment
        Fragment existing = getChildFragmentManager()
                .findFragmentById(R.id.classNoticeContainer);
        if (existing != null) {
            getChildFragmentManager().beginTransaction()
                    .remove(existing)
                    .commitNow();
        }

        classNoticeContainer.setVisibility(View.GONE);
        showClassList();
    }

    // ── Shared helpers ────────────────────────────────────────────────────
    /**
     * Replace classNoticeContainer with NoticeListFragment for the given DB
     * path.
     */
    private void showNoticeList(String dbPath) {
        classNoticeContainer.setVisibility(View.VISIBLE);

        NoticeListFragment nlf = NoticeListFragment.newInstance(dbPath, role, uid);
        getChildFragmentManager().beginTransaction()
                .replace(R.id.classNoticeContainer, nlf)
                .commitNow();
    }

    private void onFabClick() {
        if (selectedCourse == null || selectedYear == null) {
            Toast.makeText(requireContext(), "Select a class first", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(requireContext(), AddNoticeActivity.class);
        i.putExtra(AddNoticeActivity.EXTRA_MODE, "class");
        i.putExtra(AddNoticeActivity.EXTRA_DEPARTMENT, dept);
        i.putExtra(AddNoticeActivity.EXTRA_COURSE, selectedCourse);
        i.putExtra(AddNoticeActivity.EXTRA_YEAR, selectedYear);
        i.putExtra(AddNoticeActivity.EXTRA_CREATOR_UID, uid);
        i.putExtra(AddNoticeActivity.EXTRA_CREATOR_NAME, name);
        startActivity(i);
    }

    private boolean isStudent() {
        return "student".equalsIgnoreCase(role);
    }

    private static String buildClassPath(String dept, String course, String year) {
        return "notices/class/"
                + sanitize(dept) + "/"
                + sanitize(course) + "/"
                + sanitize(year);
    }

    private static String sanitize(String s) {
        if (s == null) {
            return "_";
        }
        return s.trim()
                .replace(" ", "_").replace(".", "_")
                .replace("#", "_").replace("$", "_")
                .replace("[", "_").replace("]", "_");
    }

    // ── Inner adapter ─────────────────────────────────────────────────────
    /**
     * Simple RecyclerView adapter for the teacher's class selection list.
     */
    private static class ClassOptionAdapter
            extends RecyclerView.Adapter<ClassOptionAdapter.VH> {

        interface OnClassSelected {

            void onSelected(ClassOption option);
        }

        private final List<ClassOption> options;
        private final OnClassSelected listener;

        ClassOptionAdapter(List<ClassOption> options, OnClassSelected listener) {
            this.options = options;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_class_option, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            ClassOption o = options.get(pos);
            h.tvCourse.setText(o.course);
            h.tvYear.setText("Year " + o.year);
            h.itemView.setOnClickListener(v -> listener.onSelected(o));
        }

        @Override
        public int getItemCount() {
            return options.size();
        }

        static class VH extends RecyclerView.ViewHolder {

            android.widget.TextView tvCourse, tvYear;

            VH(@NonNull View v) {
                super(v);
                tvCourse = v.findViewById(R.id.tvClassCourse);
                tvYear = v.findViewById(R.id.tvClassYear);
            }
        }
    }
}
