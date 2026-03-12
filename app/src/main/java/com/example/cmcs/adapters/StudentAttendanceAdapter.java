package com.example.cmcs.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cmcs.R;
import com.example.cmcs.models.StudentModel;

import de.hdodenhof.circleimageview.CircleImageView;

import java.util.ArrayList;
import java.util.List;

public class StudentAttendanceAdapter extends RecyclerView.Adapter<StudentAttendanceAdapter.ViewHolder> {

    public static class StudentAttendanceState {

        public StudentModel student;
        public boolean isPresent;
        public String method; // "qr" or "manual"
        public boolean isSelected;

        public StudentAttendanceState(StudentModel student, boolean isPresent, String method) {
            this.student = student;
            this.isPresent = isPresent;
            this.method = method;
            this.isSelected = false;
        }
    }

    public interface OnSelectionChangedListener {

        void onSelectionModeStarted();

        void onSelectionChanged(int selectedCount);
    }

    private Context context;
    private List<StudentAttendanceState> fullStudentList;
    private List<StudentAttendanceState> filteredStudentList;
    private boolean isSelectionMode = false;
    private OnSelectionChangedListener selectionListener;

    public StudentAttendanceAdapter(Context context, List<StudentAttendanceState> fullStudentList, OnSelectionChangedListener listener) {
        this.context = context;
        this.fullStudentList = fullStudentList;
        this.filteredStudentList = new ArrayList<>(fullStudentList);
        this.selectionListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student_attendance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudentAttendanceState state = filteredStudentList.get(position);
        StudentModel student = state.student;

        holder.tvStudentName.setText(student.getName());
        holder.tvRollNumber.setText("Roll: " + student.getRollNumber());

        if (student.getProfileImageUrl() != null && !student.getProfileImageUrl().isEmpty()) {
            Glide.with(context).load(student.getProfileImageUrl()).placeholder(R.drawable.ic_drawer_profile).into(holder.ivProfile);
        } else {
            holder.ivProfile.setImageResource(R.drawable.ic_drawer_profile);
        }

        if (state.isPresent) {
            holder.cardStudent.setCardBackgroundColor(Color.parseColor("#E8F5E9")); // Light Green
            holder.ivAttendanceMethod.setVisibility(View.VISIBLE);
            if ("qr".equals(state.method)) {
                holder.ivAttendanceMethod.setImageResource(R.drawable.ic_qr);
            } else {
                holder.ivAttendanceMethod.setImageResource(R.drawable.ic_manual);
            }
        } else {
            holder.cardStudent.setCardBackgroundColor(Color.WHITE);
            holder.ivAttendanceMethod.setVisibility(View.GONE);
        }

        if (isSelectionMode) {
            holder.cbSelect.setVisibility(View.VISIBLE);
            holder.cbSelect.setChecked(state.isSelected);
        } else {
            holder.cbSelect.setVisibility(View.GONE);
            holder.cbSelect.setChecked(false);
            state.isSelected = false; // reset
        }

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                state.isSelected = !state.isSelected;
                holder.cbSelect.setChecked(state.isSelected);
                notifySelectionChange();
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                isSelectionMode = true;
                state.isSelected = true;
                notifyDataSetChanged();
                if (selectionListener != null) {
                    selectionListener.onSelectionModeStarted();
                }
                notifySelectionChange();
                return true;
            }
            return false;
        });

        holder.cbSelect.setOnClickListener(v -> {
            state.isSelected = holder.cbSelect.isChecked();
            notifySelectionChange();
        });
    }

    private void notifySelectionChange() {
        int count = 0;
        for (StudentAttendanceState s : fullStudentList) {
            if (s.isSelected) {
                count++;
            }
        }

        if (count == 0 && isSelectionMode) {
            isSelectionMode = false;
            notifyDataSetChanged();
        }

        if (selectionListener != null) {
            selectionListener.onSelectionChanged(count);
        }
    }

    public void exitSelectionMode() {
        isSelectionMode = false;
        for (StudentAttendanceState s : fullStudentList) {
            s.isSelected = false;
        }
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(0);
        }
    }

    public List<StudentAttendanceState> getSelectedStudents() {
        List<StudentAttendanceState> selected = new ArrayList<>();
        for (StudentAttendanceState s : fullStudentList) {
            if (s.isSelected) {
                selected.add(s);
            }
        }
        return selected;
    }

    public void updateData(List<StudentAttendanceState> newList) {
        this.fullStudentList = new ArrayList<>(newList);
        this.filteredStudentList = new ArrayList<>(newList);
        if (isSelectionMode) {
            exitSelectionMode();
        } else {
            notifyDataSetChanged();
        }
    }

    public void filter(String query) {
        filteredStudentList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredStudentList.addAll(fullStudentList);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (StudentAttendanceState state : fullStudentList) {
                if (state.student.getName() != null && state.student.getName().toLowerCase().contains(lowerQuery)) {
                    filteredStudentList.add(state);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return filteredStudentList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        CardView cardStudent;
        CheckBox cbSelect;
        CircleImageView ivProfile;
        TextView tvStudentName, tvRollNumber;
        ImageView ivAttendanceMethod;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardStudent = itemView.findViewById(R.id.cardStudent);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            ivProfile = itemView.findViewById(R.id.ivProfile);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvRollNumber = itemView.findViewById(R.id.tvRollNumber);
            ivAttendanceMethod = itemView.findViewById(R.id.ivAttendanceMethod);
        }
    }
}
