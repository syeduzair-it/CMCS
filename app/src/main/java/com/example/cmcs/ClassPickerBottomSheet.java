package com.example.cmcs;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * BottomSheetDialogFragment shown when a teacher taps FAB on the CLASS tab.
 * Queries users/ in the teacher's department, collects unique (course, year)
 * pairs, and lets the teacher pick one to open AddNoticeActivity.
 */
public class ClassPickerBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_DEPT = "dept";
    private static final String ARG_CREATOR_UID = "creator_uid";
    private static final String ARG_CREATOR_NAME = "creator_name";

    public static ClassPickerBottomSheet newInstance(String dept,
            String creatorUid,
            String creatorName) {
        ClassPickerBottomSheet sheet = new ClassPickerBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_DEPT, dept);
        b.putString(ARG_CREATOR_UID, creatorUid);
        b.putString(ARG_CREATOR_NAME, creatorName);
        sheet.setArguments(b);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_class_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String dept = getArguments() != null ? getArguments().getString(ARG_DEPT) : null;
        String creatorUid = getArguments() != null ? getArguments().getString(ARG_CREATOR_UID) : null;
        String creatorName = getArguments() != null ? getArguments().getString(ARG_CREATOR_NAME) : null;

        RecyclerView rv = view.findViewById(R.id.classPickerRecyclerView);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<ClassOption> options = new ArrayList<>();
        ClassOptionAdapter adapter = new ClassOptionAdapter(options, option -> {
            dismiss();
            Intent i = new Intent(requireContext(), AddNoticeActivity.class);
            i.putExtra(AddNoticeActivity.EXTRA_MODE, "class");
            i.putExtra(AddNoticeActivity.EXTRA_DEPARTMENT, dept);
            i.putExtra(AddNoticeActivity.EXTRA_COURSE, option.course);
            i.putExtra(AddNoticeActivity.EXTRA_YEAR, option.year);
            i.putExtra(AddNoticeActivity.EXTRA_CREATOR_UID, creatorUid);
            i.putExtra(AddNoticeActivity.EXTRA_CREATOR_NAME, creatorName);
            startActivity(i);
        });
        rv.setAdapter(adapter);

        // Fetch unique (course, year) pairs from students in this department
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
                        Set<String> seen = new LinkedHashSet<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String role = child.child("role").getValue(String.class);
                            String course = child.child("course").getValue(String.class);
                            String year = child.child("year").getValue(String.class);
                            if ("student".equalsIgnoreCase(role) && course != null && year != null) {
                                String key = course + "|" + year;
                                if (seen.add(key)) {
                                    options.add(new ClassOption(course, year));
                                }
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    // ── Inner model ───────────────────────────────────────────────────────
    static class ClassOption {

        final String course, year;

        ClassOption(String course, String year) {
            this.course = course;
            this.year = year;
        }
    }

    // ── Inner adapter ─────────────────────────────────────────────────────
    static class ClassOptionAdapter extends RecyclerView.Adapter<ClassOptionAdapter.VH> {

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

            TextView tvCourse, tvYear;

            VH(@NonNull View v) {
                super(v);
                tvCourse = v.findViewById(R.id.tvClassCourse);
                tvYear = v.findViewById(R.id.tvClassYear);
            }
        }
    }
}
