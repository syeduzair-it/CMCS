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
import com.example.cmcs.models.ChatModel;
import de.hdodenhof.circleimageview.CircleImageView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the chat list screen. Binds ChatModel objects to
 * item_chat.xml rows.
 */
public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatViewHolder> {

    // ── Callback interface ─────────────────────────────────────────────────
    public interface OnChatClickListener {

        void onChatClick(ChatModel chat);
    }

    public interface OnChatLongClickListener {

        void onChatLongClick(ChatModel chat);
    }

    // ── Fields ─────────────────────────────────────────────────────────────
    private final Context context;
    private final List<ChatModel> chatList;
    private OnChatClickListener clickListener;
    private OnChatLongClickListener longClickListener;

    // ── Constructor ────────────────────────────────────────────────────────
    public ChatListAdapter(Context context, List<ChatModel> chatList) {
        this.context = context;
        this.chatList = chatList;
    }

    public void setOnChatClickListener(OnChatClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnChatLongClickListener(OnChatLongClickListener listener) {
        this.longClickListener = listener;
    }

    // ── RecyclerView overrides ─────────────────────────────────────────────
    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatModel chat = chatList.get(position);

        // Name
        holder.tvName.setText(chat.getDisplayName());

        // Last message
        String lastMsg = chat.getLastMessage();
        holder.tvLastMessage.setText(lastMsg != null && !lastMsg.isEmpty() ? lastMsg : "No messages yet");

        // Timestamp
        holder.tvTimestamp.setText(formatTimestamp(chat.getLastTimestamp()));

        // Teacher badge
        boolean isTeacher = "teacher".equalsIgnoreCase(chat.getOtherUserRole());
        holder.tvTeacherBadge.setVisibility(isTeacher ? View.VISIBLE : View.GONE);

        // Profile image
        String imageUrl = chat.getOtherUserProfileImage();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(context)
                    .load(imageUrl)
                    .apply(new RequestOptions()
                            .placeholder(R.drawable.ic_drawer_profile)
                            .error(R.drawable.ic_drawer_profile)
                            .circleCrop())
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_drawer_profile);
        }

        // Unread badge
        int unread = chat.getUnreadCount();
        if (unread > 0) {
            holder.tvUnreadBadge.setVisibility(View.VISIBLE);
            holder.tvUnreadBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            // Bold the last message preview when there are unread messages
            holder.tvLastMessage.setTypeface(null, android.graphics.Typeface.BOLD);
            holder.tvLastMessage.setTextColor(context.getResources().getColor(
                    android.R.color.holo_purple, context.getTheme()));
        } else {
            holder.tvUnreadBadge.setVisibility(View.GONE);
            holder.tvLastMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
            holder.tvLastMessage.setTextColor(context.getResources().getColor(
                    R.color.textSecondary, context.getTheme()));
        }

        // Row click
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onChatClick(chat);
            }
        });

        // Row long click
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onChatLongClick(chat);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    // ── Utility – timestamp formatting ─────────────────────────────────────
    /**
     * Returns: – "HH:mm" if the timestamp is from today – "dd/MM/yy" for older
     * messages
     */
    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }

        Date msgDate = new Date(timestamp);
        Calendar msgCal = Calendar.getInstance();
        msgCal.setTime(msgDate);

        Calendar today = Calendar.getInstance();

        boolean isToday = msgCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && msgCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);

        SimpleDateFormat sdf = isToday
                ? new SimpleDateFormat("HH:mm", Locale.getDefault())
                : new SimpleDateFormat("dd/MM/yy", Locale.getDefault());

        return sdf.format(msgDate);
    }

    // ── ViewHolder ─────────────────────────────────────────────────────────
    static class ChatViewHolder extends RecyclerView.ViewHolder {

        CircleImageView ivAvatar;
        TextView tvName;
        TextView tvLastMessage;
        TextView tvTimestamp;
        TextView tvUnreadBadge;
        TextView tvTeacherBadge;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.item_avatar);
            tvName = itemView.findViewById(R.id.item_name);
            tvLastMessage = itemView.findViewById(R.id.item_last_message);
            tvTimestamp = itemView.findViewById(R.id.item_timestamp);
            tvUnreadBadge = itemView.findViewById(R.id.item_unread_badge);
            tvTeacherBadge = itemView.findViewById(R.id.item_teacher_badge);
        }
    }
}
