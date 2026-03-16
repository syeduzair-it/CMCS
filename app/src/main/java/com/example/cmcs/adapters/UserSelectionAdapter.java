package com.example.cmcs.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.cmcs.R;
import com.example.cmcs.models.UserModel;
import de.hdodenhof.circleimageview.CircleImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for SelectUserActivity. Binds UserModel objects to
 * item_user.xml rows.
 *
 * Supports real-time search filtering via {@link #filter(String)}.
 */
public class UserSelectionAdapter extends RecyclerView.Adapter<UserSelectionAdapter.UserViewHolder> {

    // ── Callback ──────────────────────────────────────────────────────────
    public interface OnUserClickListener {
        void onUserClick(UserModel user);
    }
    
    public interface OnUserCheckListener {
        void onUserCheck(UserModel user, boolean isChecked);
    }

    // ── Fields ────────────────────────────────────────────────────────────
    private final Context context;
    private final List<UserModel> fullList;     // master list (never filtered)
    private final List<UserModel> displayList;  // shown in RecyclerView
    private OnUserClickListener clickListener;
    private OnUserCheckListener checkListener;
    
    // Selection mode
    private boolean selectionMode = false;
    private final List<String> selectedUids = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────────
    public UserSelectionAdapter(Context context, List<UserModel> users) {
        this.context = context;
        this.fullList = new ArrayList<>(users);
        this.displayList = new ArrayList<>(users);
    }

    public void setOnUserClickListener(OnUserClickListener listener) {
        this.clickListener = listener;
    }
    
    public void setOnUserCheckListener(OnUserCheckListener listener) {
        this.checkListener = listener;
    }
    
    /**
     * Enable or disable selection mode with checkboxes
     */
    public void setSelectionMode(boolean enabled) {
        this.selectionMode = enabled;
        if (!enabled) {
            selectedUids.clear();
        }
        notifyDataSetChanged();
    }

    // ── Data update ───────────────────────────────────────────────────────
    /**
     * Replaces the full dataset and resets any active search filter.
     */
    public void updateData(List<UserModel> users) {
        fullList.clear();
        fullList.addAll(users);
        displayList.clear();
        displayList.addAll(users);
        notifyDataSetChanged();
    }

    /**
     * Filters displayList to entries whose name contains {@code query}
     * (case-insensitive). Pass an empty string to reset.
     */
    public void filter(String query) {
        displayList.clear();
        if (query == null || query.trim().isEmpty()) {
            displayList.addAll(fullList);
        } else {
            String q = query.trim().toLowerCase();
            for (UserModel u : fullList) {
                if (u.getName() != null && u.getName().toLowerCase().contains(q)) {
                    displayList.add(u);
                }
            }
        }
        notifyDataSetChanged();
    }

    // ── RecyclerView overrides ────────────────────────────────────────────
    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        UserModel user = displayList.get(position);

        holder.tvName.setText(user.getName() != null ? user.getName() : "Unknown");
        holder.tvSubtitle.setText(user.getSubtitle());

        boolean isTeacher = "teacher".equalsIgnoreCase(user.getRole());
        holder.tvTeacherBadge.setVisibility(isTeacher ? View.VISIBLE : View.GONE);

        // Profile image
        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            Glide.with(context)
                    .load(user.getProfileImage())
                    .apply(new RequestOptions()
                            .placeholder(R.drawable.ic_drawer_profile)
                            .error(R.drawable.ic_drawer_profile)
                            .circleCrop())
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_drawer_profile);
        }
        
        // Selection mode
        if (selectionMode) {
            holder.checkbox.setVisibility(View.VISIBLE);
            boolean isSelected = selectedUids.contains(user.getAuthUid());
            holder.checkbox.setChecked(isSelected);
            
            holder.itemView.setOnClickListener(v -> {
                boolean newState = !holder.checkbox.isChecked();
                holder.checkbox.setChecked(newState);
                
                if (newState) {
                    selectedUids.add(user.getAuthUid());
                } else {
                    selectedUids.remove(user.getAuthUid());
                }
                
                if (checkListener != null) {
                    checkListener.onUserCheck(user, newState);
                }
            });
            
            holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!selectedUids.contains(user.getAuthUid())) {
                        selectedUids.add(user.getAuthUid());
                        if (checkListener != null) {
                            checkListener.onUserCheck(user, true);
                        }
                    }
                } else {
                    selectedUids.remove(user.getAuthUid());
                    if (checkListener != null) {
                        checkListener.onUserCheck(user, false);
                    }
                }
            });
        } else {
            holder.checkbox.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onUserClick(user);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    // ── ViewHolder ────────────────────────────────────────────────────────
    public static class UserViewHolder extends RecyclerView.ViewHolder {

        CircleImageView ivAvatar;
        TextView tvName;
        TextView tvSubtitle;
        TextView tvTeacherBadge;
        android.widget.CheckBox checkbox;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.user_avatar);
            tvName = itemView.findViewById(R.id.user_name);
            tvSubtitle = itemView.findViewById(R.id.user_subtitle);
            tvTeacherBadge = itemView.findViewById(R.id.user_teacher_badge);
            checkbox = itemView.findViewById(R.id.user_checkbox);
        }
    }
}
