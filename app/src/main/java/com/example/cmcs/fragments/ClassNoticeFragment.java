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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the CLASS tab of the Notice board.
 *
 * Student flow:
 *   Directly loads NoticeListFragment for notices/class/<dept>/<course>/<year>.
 *   No class list shown, no FAB.
 *
 * Teacher flow:
 *   Step 1 – Shows a fixed class list based on the teacher's department.
 *   Step 2 – On class row click: shows NoticeListFragment for that class.
 *   Step 3 – FAB: posts a new notice to the selected class.
 *   Back press while viewing class notices → returns to class list.
 *
 * Fixed class map (no Firebase reads for class list):
 *   computer   → BCA 1/2/3, BSC 1/2/3, MCA 1/2
 *   management → BBA 1/2/3, BCOM 1/2/3, MBA 1/2
 */
public class ClassNoticeFragment extends Fragment {

    // ── Args ──────────────────────────────────────────────────────────────
    private static final String ARG_ROLE   = "role";
    private static final String ARG_UID    = "uid";
    private static final String ARG_DEPT   = "dept";
    private static final String ARG_COURSE = "course";
    private static final String ARG_YEAR   = "year";
    private static final String ARG_NAME   = "name";

    // ── Saved state ───────────────────────────────────────────────────────
    private static final String STATE_SELECTED_COURSE = "sel_course";
    private static final String STATE_SELECTED_YEAR   = "sel_year";
    private static final String STATE_SHOW_LIST        = "show_list";

    // ── Fixed class map: department → list of (course, year) ─────────────
    private static final Map<String, List<ClassOption>> DEPT_CLASSES = new HashMap<>();
    static {
        DEPT_CLASSES.put("computer", buildOptions(
                new String[]{"BCA", "BSC", "MCA"},
                new int[][]{{1,2,3},{1,2,3},{1,2}}
        ));
        DEPT_CLASSES.put("management", buildOptions(
                new String[]{"BBA", "BCOM", "MBA"},
                new int[][]{{1,2,3},{1,2,3},{1,2}}
        ));
    }

    private static List<ClassOption> buildOptions(String[] courses, int[][] years) {
        List<ClassOption> list = new ArrayList<>();
        for (int i = 0; i < courses.length; i++) {
            for (int y : years[i]) {
                list.add(new ClassOption(courses[i], String.valueOf(y)));
            }
        }
        return list;
    }

    // ── Fields ────────────────────────────────────────────────────────────
    private String role, uid, dept, ownCourse, ownYear, name;

    private String  selectedCourse = null;
    private String  selectedYear   = null;
    private boolean showingList    = false;

    private RecyclerView           rvClassList;
    private View                   classNoticeContainer;
    private FloatingActionButton   fabClassNotice;
    private OnBackPressedCallback  backCallback;

    // ── Inner model ───────────────────────────────────────────────────────
    static class ClassOption {
        final String course, year;
        ClassOption(String c, String y) { course = c; year = y; }
    }

    // ── Factory ───────────────────────────────────────────────────────────
    public static ClassNoticeFragment newInstance(String role, String uid,
            String dept, String course, String year, String name) {
        ClassNoticeFragment f = new ClassNoticeFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ROLE,   role);
        b.putString(ARG_UID,    uid);
        b.putString(ARG_DEPT,   dept);
        b.putString(ARG_COURSE, course);
        b.putString(ARG_YEAR,   year);
        b.putString(ARG_NAME,   name);
        f.setArguments(b);
        return f;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            role      = getArguments().getString(ARG_ROLE,   "");
            uid       = getArguments().getString(ARG_UID,    "");
            dept      = getArguments().getString(ARG_DEPT,   "");
            ownCourse = getArguments().getString(ARG_COURSE, "");
            ownYear   = getArguments().getString(ARG_YEAR,   "");
            name      = getArguments().getString(ARG_NAME,   "");
        }
        if (savedInstanceState != null) {
            selectedCourse = savedInstanceState.getString(STATE_SELECTED_COURSE);
            selectedYear   = savedInstanceState.getString(STATE_SELECTED_YEAR);
            showingList    = savedInstanceState.getBoolean(STATE_SHOW_LIST, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_class_notice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvClassList          = view.findViewById(R.id.rvClassList);
        classNoticeContainer = view.findViewById(R.id.classNoticeContainer);
        fabClassNotice       = view.findViewById(R.id.fabClassNotice);

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
        out.putString(STATE_SELECTED_YEAR,   selectedYear);
        out.putBoolean(STATE_SHOW_LIST,       showingList);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (backCallback != null) { backCallback.remove(); backCallback = null; }
    }

    // ── Student Flow ──────────────────────────────────────────────────────
    private void setupStudentFlow() {
        rvClassList.setVisibility(View.GONE);
        fabClassNotice.setVisibility(View.GONE);
        String dbPath = buildClassPath(dept, ownCourse, ownYear);
        showNoticeList(dbPath);
    }

    // ── Teacher Flow ──────────────────────────────────────────────────────
    private void setupTeacherFlow() {
        fabClassNotice.setVisibility(View.VISIBLE);
        fabClassNotice.setOnClickListener(v -> onFabClick());

        backCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { returnToClassList(); }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), backCallback);

        if (showingList && selectedCourse != null) {
            // Restore after rotation
            rvClassList.setVisibility(View.GONE);
            classNoticeContainer.setVisibility(View.VISIBLE);
            showNoticeList(buildClassPath(dept, selectedCourse, selectedYear));
            backCallback.setEnabled(true);
        } else {
            showClassList();
        }
    }

    /**
     * Populate the RecyclerView with the fixed class list for this department.
     * No Firebase read needed.
     */
    private void showClassList() {
        rvClassList.setVisibility(View.VISIBLE);
        classNoticeContainer.setVisibility(View.GONE);
        if (backCallback != null) backCallback.setEnabled(false);

        // Look up fixed list; fall back to empty if dept not mapped
        String deptKey = dept != null ? dept.toLowerCase().trim() : "";
        List<ClassOption> options = DEPT_CLASSES.containsKey(deptKey)
                ? DEPT_CLASSES.get(deptKey)
                : new ArrayList<>();

        rvClassList.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvClassList.setAdapter(new ClassOptionAdapter(options, this::onClassSelected));
    }

    private void onClassSelected(ClassOption option) {
        selectedCourse = option.course;
        selectedYear   = option.year;
        showingList    = true;

        rvClassList.setVisibility(View.GONE);
        classNoticeContainer.setVisibility(View.VISIBLE);
        if (backCallback != null) backCallback.setEnabled(true);

        showNoticeList(buildClassPath(dept, selectedCourse, selectedYear));
    }

    private void returnToClassList() {
        selectedCourse = null;
        selectedYear   = null;
        showingList    = false;

        Fragment existing = getChildFragmentManager().findFragmentById(R.id.classNoticeContainer);
        if (existing != null) {
            getChildFragmentManager().beginTransaction().remove(existing).commitNow();
        }
        classNoticeContainer.setVisibility(View.GONE);
        showClassList();
    }

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
        i.putExtra(AddNoticeActivity.EXTRA_MODE,         "class");
        i.putExtra(AddNoticeActivity.EXTRA_DEPARTMENT,   dept);
        i.putExtra(AddNoticeActivity.EXTRA_COURSE,       selectedCourse);
        i.putExtra(AddNoticeActivity.EXTRA_YEAR,         selectedYear);
        i.putExtra(AddNoticeActivity.EXTRA_CREATOR_UID,  uid);
        i.putExtra(AddNoticeActivity.EXTRA_CREATOR_NAME, name);
        startActivity(i);
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private boolean isStudent() { return "student".equalsIgnoreCase(role); }

    private static String buildClassPath(String dept, String course, String year) {
        return "notices/class/"
                + sanitize(dept) + "/"
                + normalizeCourse(course) + "/"
                + normalizeYear(year);
    }

    /** BCA → bca, BSC → bsc, etc. */
    static String normalizeCourse(String course) {
        if (course == null) return "_";
        return course.trim().toLowerCase();
    }

    /** "1" → "1st_year", "2" → "2nd_year", "3" → "3rd_year", else pass-through sanitized */
    static String normalizeYear(String year) {
        if (year == null) return "_";
        switch (year.trim()) {
            case "1": return "1st_year";
            case "2": return "2nd_year";
            case "3": return "3rd_year";
            default:  return sanitize(year); // already normalized (e.g. "1st_year")
        }
    }

    private static String sanitize(String s) {
        if (s == null) return "_";
        return s.trim().replace(" ", "_").replace(".", "_")
                .replace("#", "_").replace("$", "_")
                .replace("[", "_").replace("]", "_");
    }

    // ── Inner adapter ─────────────────────────────────────────────────────
    private static class ClassOptionAdapter
            extends RecyclerView.Adapter<ClassOptionAdapter.VH> {

        interface OnClassSelected { void onSelected(ClassOption option); }

        private final List<ClassOption>  options;
        private final OnClassSelected    listener;

        ClassOptionAdapter(List<ClassOption> options, OnClassSelected listener) {
            this.options  = options;
            this.listener = listener;
        }

        @NonNull @Override
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

        @Override public int getItemCount() { return options.size(); }

        static class VH extends RecyclerView.ViewHolder {
            android.widget.TextView tvCourse, tvYear;
            VH(@NonNull View v) {
                super(v);
                tvCourse = v.findViewById(R.id.tvClassCourse);
                tvYear   = v.findViewById(R.id.tvClassYear);
            }
        }
    }
}
