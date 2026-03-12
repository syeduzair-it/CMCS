package com.example.cmcs.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cmcs.R;
import com.example.cmcs.models.StudentModel;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class StudentSwipeAdapter extends RecyclerView.Adapter<StudentSwipeAdapter.StudentViewHolder> {

    private Context context;
    private List<StudentModel> studentList;

    public StudentSwipeAdapter(Context context, List<StudentModel> studentList) {
        this.context = context;
        this.studentList = studentList;
    }

    @NonNull
    @Override
    public StudentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_student_attendance_card, parent, false);
        return new StudentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StudentViewHolder holder, int position) {
        StudentModel student = studentList.get(position);
        holder.tvStudentName.setText(student.getName() != null ? student.getName() : "Unknown");
        holder.tvStudentId.setText("ID: " + (student.getStudentId() != null ? student.getStudentId() : "N/A"));

        // Load profile image using Glide. Note: Requires Glide dependency in build.gradle
        String profileUrl = student.getProfileImageUrl();
        if (profileUrl != null && !profileUrl.isEmpty()) {
            Glide.with(context)
                    .load(profileUrl)
                    .placeholder(R.mipmap.ic_launcher_round) // Assumed generic placeholder
                    .into(holder.civProfileImage);
        } else {
            holder.civProfileImage.setImageResource(R.mipmap.ic_launcher_round);
        }
    }

    @Override
    public int getItemCount() {
        return studentList.isEmpty() ? 0 : 1;
    }

    // Method to remove item (used securely during swipe processing in Activity)
    public StudentModel removeItem(int position) {
        StudentModel removed = studentList.remove(position);
        notifyItemRemoved(position);
        return removed;
    }

    // Method to restore item explicitly
    public void restoreItem(StudentModel student, int position) {
        studentList.add(position, student);
        notifyItemInserted(position);
    }

    public static class StudentViewHolder extends RecyclerView.ViewHolder {

        CircleImageView civProfileImage;
        TextView tvStudentName;
        TextView tvStudentId;

        public StudentViewHolder(@NonNull View itemView) {
            super(itemView);
            civProfileImage = itemView.findViewById(R.id.civProfileImage);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvStudentId = itemView.findViewById(R.id.tvStudentId);
        }
    }
}
