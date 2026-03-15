package com.example.cmcs.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cmcs.ImageViewerActivity;
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
 * Supports multiple message types:
 * - Text messages (with clickable links)
 * - Image messages (with preview and full-screen viewer)
 * - Video messages (with thumbnail and play button)
 * - File messages (with file icon and name)
 *
 * View types are determined by message type and sender.
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // View type constants
    private static final int VIEW_TYPE_TEXT_SENT = 1;
    private static final int VIEW_TYPE_TEXT_RECEIVED = 2;
    private static final int VIEW_TYPE_IMAGE_SENT = 3;
    private static final int VIEW_TYPE_IMAGE_RECEIVED = 4;
    private static final int VIEW_TYPE_VIDEO_SENT = 5;
    private static final int VIEW_TYPE_VIDEO_RECEIVED = 6;
    private static final int VIEW_TYPE_FILE_SENT = 7;
    private static final int VIEW_TYPE_FILE_RECEIVED = 8;

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
                int oldStatus = o.getStatusFor(otherUid);
                int newStatus = n.getStatusFor(otherUid);
                return Objects.equals(o.getMessage(), n.getMessage())
                        && Objects.equals(o.getMediaUrl(), n.getMediaUrl())
                        && oldStatus == newStatus;
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
        MessageModel msg = messages.get(position);
        boolean isSent = myUid.equals(msg.getSenderId());
        String messageType = msg.getMessageType();

        if (MessageModel.TYPE_IMAGE.equals(messageType)) {
            return isSent ? VIEW_TYPE_IMAGE_SENT : VIEW_TYPE_IMAGE_RECEIVED;
        } else if (MessageModel.TYPE_VIDEO.equals(messageType)) {
            return isSent ? VIEW_TYPE_VIDEO_SENT : VIEW_TYPE_VIDEO_RECEIVED;
        } else if (MessageModel.TYPE_FILE.equals(messageType)) {
            return isSent ? VIEW_TYPE_FILE_SENT : VIEW_TYPE_FILE_RECEIVED;
        } else {
            return isSent ? VIEW_TYPE_TEXT_SENT : VIEW_TYPE_TEXT_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v;

        switch (viewType) {
            case VIEW_TYPE_TEXT_SENT:
                v = inflater.inflate(R.layout.item_message_sent, parent, false);
                return new TextSentViewHolder(v);
            case VIEW_TYPE_TEXT_RECEIVED:
                v = inflater.inflate(R.layout.item_message_received, parent, false);
                return new TextReceivedViewHolder(v);
            case VIEW_TYPE_IMAGE_SENT:
                v = inflater.inflate(R.layout.item_message_image_sent, parent, false);
                return new ImageSentViewHolder(v);
            case VIEW_TYPE_IMAGE_RECEIVED:
                v = inflater.inflate(R.layout.item_message_image_received, parent, false);
                return new ImageReceivedViewHolder(v);
            case VIEW_TYPE_VIDEO_SENT:
                v = inflater.inflate(R.layout.item_message_video_sent, parent, false);
                return new VideoSentViewHolder(v);
            case VIEW_TYPE_VIDEO_RECEIVED:
                v = inflater.inflate(R.layout.item_message_video_received, parent, false);
                return new VideoReceivedViewHolder(v);
            case VIEW_TYPE_FILE_SENT:
                v = inflater.inflate(R.layout.item_message_file_sent, parent, false);
                return new FileSentViewHolder(v);
            case VIEW_TYPE_FILE_RECEIVED:
                v = inflater.inflate(R.layout.item_message_file_received, parent, false);
                return new FileReceivedViewHolder(v);
            default:
                v = inflater.inflate(R.layout.item_message_sent, parent, false);
                return new TextSentViewHolder(v);
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

        if (holder instanceof TextSentViewHolder) {
            ((TextSentViewHolder) holder).bind(msg, otherUid);
        } else if (holder instanceof TextReceivedViewHolder) {
            ((TextReceivedViewHolder) holder).bind(msg);
        } else if (holder instanceof ImageSentViewHolder) {
            ((ImageSentViewHolder) holder).bind(msg, otherUid);
        } else if (holder instanceof ImageReceivedViewHolder) {
            ((ImageReceivedViewHolder) holder).bind(msg);
        } else if (holder instanceof VideoSentViewHolder) {
            ((VideoSentViewHolder) holder).bind(msg, otherUid);
        } else if (holder instanceof VideoReceivedViewHolder) {
            ((VideoReceivedViewHolder) holder).bind(msg);
        } else if (holder instanceof FileSentViewHolder) {
            ((FileSentViewHolder) holder).bind(msg, otherUid);
        } else if (holder instanceof FileReceivedViewHolder) {
            ((FileReceivedViewHolder) holder).bind(msg);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewHolders - Text Messages
    // ─────────────────────────────────────────────────────────────────────────
    public static class TextSentViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMessage;
        private final TextView tvTimestamp;
        private final View viewStatusDot;
        private final TextView tvForwarded;
        private final View layoutReply;
        private final TextView tvReplyText;

        public TextSentViewHolder(@NonNull View itemView) {
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
            tvMessage.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
            tvMessage.setLinksClickable(true);
            tvMessage.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            
            tvTimestamp.setText(formatTime(msg.getTimestamp()));
            tvForwarded.setVisibility(msg.isForwarded() ? View.VISIBLE : View.GONE);
            
            if (msg.getReplyToText() != null && !msg.getReplyToText().isEmpty()) {
                layoutReply.setVisibility(View.VISIBLE);
                tvReplyText.setText(msg.getReplyToText());
            } else {
                layoutReply.setVisibility(View.GONE);
            }

            Context ctx = itemView.getContext();
            int colorRes;
            switch (msg.getStatusFor(otherUid)) {
                case MessageModel.STATUS_READ:
                    colorRes = R.color.statusRead;
                    break;
                case MessageModel.STATUS_DELIVERED:
                    colorRes = R.color.statusDelivered;
                    break;
                default:
                    colorRes = android.R.color.white;
                    break;
            }
            viewStatusDot.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, colorRes)));
        }
    }

    public static class TextReceivedViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMessage;
        private final TextView tvTimestamp;
        private final TextView tvForwarded;
        private final View layoutReply;
        private final TextView tvReplyText;

        public TextReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvForwarded = itemView.findViewById(R.id.tv_forwarded);
            layoutReply = itemView.findViewById(R.id.layout_reply);
            tvReplyText = itemView.findViewById(R.id.tv_reply_text);
        }

        public void bind(MessageModel msg) {
            tvMessage.setText(msg.getMessage());
            tvMessage.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
            tvMessage.setLinksClickable(true);
            tvMessage.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            
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
    // ViewHolders - Image Messages
    // ─────────────────────────────────────────────────────────────────────────
    public static class ImageSentViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivImagePreview;
        private final TextView tvTimestamp;
        private final View viewStatusDot;

        public ImageSentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImagePreview = itemView.findViewById(R.id.iv_image_preview);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            viewStatusDot = itemView.findViewById(R.id.view_status_dot);
        }

        public void bind(MessageModel msg, String otherUid) {
            Context ctx = itemView.getContext();
            
            // Load image with Glide
            Glide.with(ctx)
                    .load(msg.getMediaUrl())
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_image)
                    .into(ivImagePreview);
            
            // Click to open full screen
            ivImagePreview.setOnClickListener(v -> {
                Intent intent = new Intent(ctx, ImageViewerActivity.class);
                intent.putExtra(ImageViewerActivity.EXTRA_IMAGE_URL, msg.getMediaUrl());
                ctx.startActivity(intent);
            });
            
            tvTimestamp.setText(formatTime(msg.getTimestamp()));
            
            int colorRes;
            switch (msg.getStatusFor(otherUid)) {
                case MessageModel.STATUS_READ:
                    colorRes = R.color.statusRead;
                    break;
                case MessageModel.STATUS_DELIVERED:
                    colorRes = R.color.statusDelivered;
                    break;
                default:
                    colorRes = android.R.color.white;
                    break;
            }
            viewStatusDot.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, colorRes)));
        }
    }

    public static class ImageReceivedViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivImagePreview;
        private final TextView tvTimestamp;

        public ImageReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImagePreview = itemView.findViewById(R.id.iv_image_preview);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
        }

        public void bind(MessageModel msg) {
            Context ctx = itemView.getContext();
            
            Glide.with(ctx)
                    .load(msg.getMediaUrl())
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_image)
                    .into(ivImagePreview);
            
            ivImagePreview.setOnClickListener(v -> {
                Intent intent = new Intent(ctx, ImageViewerActivity.class);
                intent.putExtra(ImageViewerActivity.EXTRA_IMAGE_URL, msg.getMediaUrl());
                ctx.startActivity(intent);
            });
            
            tvTimestamp.setText(formatTime(msg.getTimestamp()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewHolders - Video Messages
    // ─────────────────────────────────────────────────────────────────────────
    public static class VideoSentViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivVideoThumbnail;
        private final TextView tvTimestamp;
        private final View viewStatusDot;

        public VideoSentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivVideoThumbnail = itemView.findViewById(R.id.iv_video_thumbnail);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            viewStatusDot = itemView.findViewById(R.id.view_status_dot);
        }

        public void bind(MessageModel msg, String otherUid) {
            Context ctx = itemView.getContext();
            
            // Load video thumbnail
            Glide.with(ctx)
                    .load(msg.getMediaUrl())
                    .placeholder(R.drawable.ic_video)
                    .error(R.drawable.ic_video)
                    .into(ivVideoThumbnail);
            
            // Click to play video
            ivVideoThumbnail.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(msg.getMediaUrl()), "video/*");
                    ctx.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(ctx, "Cannot open video", Toast.LENGTH_SHORT).show();
                }
            });
            
            tvTimestamp.setText(formatTime(msg.getTimestamp()));
            
            int colorRes;
            switch (msg.getStatusFor(otherUid)) {
                case MessageModel.STATUS_READ:
                    colorRes = R.color.statusRead;
                    break;
                case MessageModel.STATUS_DELIVERED:
                    colorRes = R.color.statusDelivered;
                    break;
                default:
                    colorRes = android.R.color.white;
                    break;
            }
            viewStatusDot.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, colorRes)));
        }
    }

    public static class VideoReceivedViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivVideoThumbnail;
        private final TextView tvTimestamp;

        public VideoReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            ivVideoThumbnail = itemView.findViewById(R.id.iv_video_thumbnail);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
        }

        public void bind(MessageModel msg) {
            Context ctx = itemView.getContext();
            
            Glide.with(ctx)
                    .load(msg.getMediaUrl())
                    .placeholder(R.drawable.ic_video)
                    .error(R.drawable.ic_video)
                    .into(ivVideoThumbnail);
            
            ivVideoThumbnail.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(msg.getMediaUrl()), "video/*");
                    ctx.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(ctx, "Cannot open video", Toast.LENGTH_SHORT).show();
                }
            });
            
            tvTimestamp.setText(formatTime(msg.getTimestamp()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewHolders - File Messages
    // ─────────────────────────────────────────────────────────────────────────
    public static class FileSentViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivFileIcon;
        private final TextView tvFileName;
        private final TextView tvTimestamp;
        private final View viewStatusDot;

        public FileSentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFileIcon = itemView.findViewById(R.id.iv_file_icon);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            viewStatusDot = itemView.findViewById(R.id.view_status_dot);
        }

        public void bind(MessageModel msg, String otherUid) {
            Context ctx = itemView.getContext();
            
            tvFileName.setText(msg.getFileName() != null ? msg.getFileName() : "File");
            tvTimestamp.setText(formatTime(msg.getTimestamp()));
            
            // Set file icon based on file type
            String fileName = msg.getFileName();
            if (fileName != null) {
                if (fileName.toLowerCase().endsWith(".pdf")) {
                    ivFileIcon.setImageResource(R.drawable.ic_document);
                } else {
                    ivFileIcon.setImageResource(R.drawable.ic_file);
                }
            }
            
            // Click to open file with chooser
            itemView.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(msg.getMediaUrl()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    // Determine MIME type
                    String mimeType = "*/*";
                    if (fileName != null) {
                        if (fileName.toLowerCase().endsWith(".pdf")) {
                            mimeType = "application/pdf";
                        } else if (fileName.toLowerCase().endsWith(".doc") || 
                                   fileName.toLowerCase().endsWith(".docx")) {
                            mimeType = "application/msword";
                        } else if (fileName.toLowerCase().endsWith(".xls") || 
                                   fileName.toLowerCase().endsWith(".xlsx")) {
                            mimeType = "application/vnd.ms-excel";
                        } else if (fileName.toLowerCase().endsWith(".ppt") || 
                                   fileName.toLowerCase().endsWith(".pptx")) {
                            mimeType = "application/vnd.ms-powerpoint";
                        } else if (fileName.toLowerCase().endsWith(".zip")) {
                            mimeType = "application/zip";
                        } else if (fileName.toLowerCase().endsWith(".txt")) {
                            mimeType = "text/plain";
                        }
                    }
                    
                    intent.setDataAndType(Uri.parse(msg.getMediaUrl()), mimeType);
                    ctx.startActivity(Intent.createChooser(intent, "Open with"));
                } catch (Exception e) {
                    Toast.makeText(ctx, "Cannot open file", Toast.LENGTH_SHORT).show();
                }
            });
            
            int colorRes;
            switch (msg.getStatusFor(otherUid)) {
                case MessageModel.STATUS_READ:
                    colorRes = R.color.statusRead;
                    break;
                case MessageModel.STATUS_DELIVERED:
                    colorRes = R.color.statusDelivered;
                    break;
                default:
                    colorRes = android.R.color.white;
                    break;
            }
            viewStatusDot.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, colorRes)));
        }
    }

    public static class FileReceivedViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivFileIcon;
        private final TextView tvFileName;
        private final TextView tvTimestamp;

        public FileReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFileIcon = itemView.findViewById(R.id.iv_file_icon);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
        }

        public void bind(MessageModel msg) {
            Context ctx = itemView.getContext();
            
            tvFileName.setText(msg.getFileName() != null ? msg.getFileName() : "File");
            tvTimestamp.setText(formatTime(msg.getTimestamp()));
            
            String fileName = msg.getFileName();
            if (fileName != null) {
                if (fileName.toLowerCase().endsWith(".pdf")) {
                    ivFileIcon.setImageResource(R.drawable.ic_document);
                } else {
                    ivFileIcon.setImageResource(R.drawable.ic_file);
                }
            }
            
            // Click to open file with chooser
            itemView.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(msg.getMediaUrl()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    // Determine MIME type
                    String mimeType = "*/*";
                    if (fileName != null) {
                        if (fileName.toLowerCase().endsWith(".pdf")) {
                            mimeType = "application/pdf";
                        } else if (fileName.toLowerCase().endsWith(".doc") || 
                                   fileName.toLowerCase().endsWith(".docx")) {
                            mimeType = "application/msword";
                        } else if (fileName.toLowerCase().endsWith(".xls") || 
                                   fileName.toLowerCase().endsWith(".xlsx")) {
                            mimeType = "application/vnd.ms-excel";
                        } else if (fileName.toLowerCase().endsWith(".ppt") || 
                                   fileName.toLowerCase().endsWith(".pptx")) {
                            mimeType = "application/vnd.ms-powerpoint";
                        } else if (fileName.toLowerCase().endsWith(".zip")) {
                            mimeType = "application/zip";
                        } else if (fileName.toLowerCase().endsWith(".txt")) {
                            mimeType = "text/plain";
                        }
                    }
                    
                    intent.setDataAndType(Uri.parse(msg.getMediaUrl()), mimeType);
                    ctx.startActivity(Intent.createChooser(intent, "Open with"));
                } catch (Exception e) {
                    Toast.makeText(ctx, "Cannot open file", Toast.LENGTH_SHORT).show();
                }
            });
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
