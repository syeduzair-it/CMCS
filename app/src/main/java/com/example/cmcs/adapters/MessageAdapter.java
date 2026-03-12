package com.example.cmcs.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.R;
import com.example.cmcs.models.MessageModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * MessageAdapter — RecyclerView adapter for the chat message list.
 *
 * View types: VIEW_TYPE_SENT = 1 (right-aligned purple bubble)
 * VIEW_TYPE_RECEIVED = 2 (left-aligned white bubble)
 *
 * Uses DiffUtil for efficient partial updates (no full notifyDataSetChanged).
 *
 * Status dot colors (sent messages only): STATUS_SENT → white dot
 * STATUS_DELIVERED → red dot STATUS_READ → green dot
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    private static final SimpleDateFormat TIME_FMT
            = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    private final String myUid;
    private final String otherUid;
    private List<MessageModel> messages = new ArrayList<>();

    public interface OnMessageLongClickListener {

        void onMessageLongClicked(MessageModel message);
    }

    private OnMessageLongClickListener longClickListener;

    public MessageAdapter(String myUid, String otherUid) {
        this.myUid = myUid;
        this.otherUid = otherUid;
    }

    public void setOnMessageLongClickListener(OnMessageLongClickListener listener) {
        this.longClickListener = listener;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data update with DiffUtil
    // ─────────────────────────────────────────────────────────────────────────
    public void submitList(final List<MessageModel> newList) {
        final List<MessageModel> oldList = this.messages;
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                String oldId = oldList.get(oldPos).getMessageId();
                String newId = newList.get(newPos).getMessageId();
                return oldId != null && oldId.equals(newId);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                MessageModel o = oldList.get(oldPos);
                MessageModel n = newList.get(newPos);
                // Contents changed if status maps changed (delivered / read)
                int oldStatus = o.getStatusFor(otherUid);
                int newStatus = n.getStatusFor(otherUid);
                return Objects.equals(o.getMessage(), n.getMessage()) && oldStatus == newStatus;
            }
        });
        this.messages = new ArrayList<>(newList);
        result.dispatchUpdatesTo(this);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView.Adapter overrides
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public int getItemViewType(int position) {
        return myUid.equals(messages.get(position).getSenderId())
                ? VIEW_TYPE_SENT
                : VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_SENT) {
            View v = inflater.inflate(R.layout.item_message_sent, parent, false);
            return new SentViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_message_received, parent, false);
            return new ReceivedViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageModel msg = messages.get(position);

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onMessageLongClicked(msg);
            }
            return true;
        });

        if (holder instanceof SentViewHolder) {
            ((SentViewHolder) holder).bind(msg, otherUid);
        } else {
            ((ReceivedViewHolder) holder).bind(msg);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewHolders
    // ─────────────────────────────────────────────────────────────────────────
    public static class SentViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvMessage;
        private final TextView tvTimestamp;
        private final View viewStatusDot;
        private final TextView tvForwarded;
        private final View layoutReply;
        private final TextView tvReplyText;

        public SentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            viewStatusDot = itemView.findViewById(R.id.view_status_dot);
            tvForwarded = itemView.findViewById(R.id.tv_forwarded);
            layoutReply = itemView.findViewById(R.id.layout_reply);
            tvReplyText = itemView.findViewById(R.id.tv_reply_text);
        }

        public void bind(MessageModel msg, String otherUid) {
            tvMessage.setText(msg.getMessage());
            tvTimestamp.setText(formatTime(msg.getTimestamp()));

            tvForwarded.setVisibility(msg.isForwarded() ? View.VISIBLE : View.GONE);
            if (msg.getReplyToText() != null && !msg.getReplyToText().isEmpty()) {
                layoutReply.setVisibility(View.VISIBLE);
                tvReplyText.setText(msg.getReplyToText());
            } else {
                layoutReply.setVisibility(View.GONE);
            }

            // Status dot color
            Context ctx = itemView.getContext();
            int colorRes;
            switch (msg.getStatusFor(otherUid)) {
                case MessageModel.STATUS_READ:
                    colorRes = R.color.statusRead;
                    break;
                case MessageModel.STATUS_DELIVERED:
                    colorRes = R.color.statusDelivered;
                    break;
                default: // STATUS_SENT
                    colorRes = android.R.color.white;
                    break;
            }
            viewStatusDot.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, colorRes)));
        }
    }

    public static class ReceivedViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvMessage;
        private final TextView tvTimestamp;
        private final TextView tvForwarded;
        private final View layoutReply;
        private final TextView tvReplyText;

        public ReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvForwarded = itemView.findViewById(R.id.tv_forwarded);
            layoutReply = itemView.findViewById(R.id.layout_reply);
            tvReplyText = itemView.findViewById(R.id.tv_reply_text);
        }

        public void bind(MessageModel msg) {
            tvMessage.setText(msg.getMessage());
            tvTimestamp.setText(formatTime(msg.getTimestamp()));

            tvForwarded.setVisibility(msg.isForwarded() ? View.VISIBLE : View.GONE);
            if (msg.getReplyToText() != null && !msg.getReplyToText().isEmpty()) {
                layoutReply.setVisibility(View.VISIBLE);
                tvReplyText.setText(msg.getReplyToText());
            } else {
                layoutReply.setVisibility(View.GONE);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────
    private static String formatTime(long timestamp) {
        if (timestamp == 0) {
            return "";
        }
        return TIME_FMT.format(new Date(timestamp));
    }
}
