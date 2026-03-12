package com.example.cmcs.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cmcs.R;
import com.example.cmcs.models.PostModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class PostFeedAdapter extends RecyclerView.Adapter<PostFeedAdapter.PostFeedViewHolder> {

    private Context context;
    private List<PostModel> postList;
    private boolean isTeacher;
    private String currentUid;

    private ExoPlayer currentlyPlayingProfile = null;
    private int playingPosition = -1;

    public PostFeedAdapter(Context context, List<PostModel> postList, boolean isTeacher) {
        this.context = context;
        this.postList = postList;
        this.isTeacher = isTeacher;
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    @NonNull
    @Override
    public PostFeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post_feed, parent, false);
        return new PostFeedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostFeedViewHolder holder, int position) {
        PostModel post = postList.get(position);

        Glide.with(context)
                .load(post.getTeacherProfileImage())
                .placeholder(android.R.color.darker_gray)
                .into(holder.ivPostProfile);

        holder.tvPostUploader.setText(post.getTeacherName());
        holder.tvPostTime.setText(getFormattedTime(post.getTimestamp()));

        if (post.getCaption() != null && !post.getCaption().trim().isEmpty()) {
            holder.tvPostCaption.setVisibility(View.VISIBLE);
            holder.tvPostCaption.setText(post.getCaption());
        } else {
            holder.tvPostCaption.setVisibility(View.GONE);
        }

        if (isTeacher && currentUid != null && currentUid.equals(post.getTeacherUid())) {
            holder.btnPostDelete.setVisibility(View.VISIBLE);
            holder.btnPostDelete.setOnClickListener(v -> deletePost(post.getPostId()));
        } else {
            holder.btnPostDelete.setVisibility(View.GONE);
        }

        Map<String, Boolean> likes = post.getLikes();
        if (likes == null) {
            likes = new HashMap<>();
        }

        holder.tvPostLikesCount.setText(likes.size() + " likes");
        if (currentUid != null && likes.containsKey(currentUid)) {
            holder.btnPostLike.setImageResource(R.drawable.ic_heart_filled);
        } else {
            holder.btnPostLike.setImageResource(R.drawable.ic_heart_empty);
        }

        holder.btnPostLike.setOnClickListener(v -> toggleLike(post));

        if ("video".equals(post.getMediaType())) {
            holder.ivPostImage.setVisibility(View.GONE);
            holder.exoPlayerView.setVisibility(View.VISIBLE);

            if (holder.exoPlayer != null) {
                holder.exoPlayer.release();
            }

            ExoPlayer player = new ExoPlayer.Builder(context).build();
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            holder.exoPlayer = player;
            holder.exoPlayerView.setPlayer(player);

            MediaItem mediaItem = MediaItem.fromUri(post.getMediaUrl());
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(false);

        } else {
            holder.exoPlayerView.setVisibility(View.GONE);
            holder.ivPostImage.setVisibility(View.VISIBLE);
            if (holder.exoPlayer != null) {
                holder.exoPlayer.release();
                holder.exoPlayer = null;
            }
            Glide.with(context)
                    .load(post.getMediaUrl())
                    .into(holder.ivPostImage);
        }
    }

    private void toggleLike(PostModel post) {
        if (currentUid == null || post.getPostId() == null) {
            return;
        }
        DatabaseReference likesRef = FirebaseDatabase.getInstance().getReference("posts").child(post.getPostId()).child("likes");
        if (post.getLikes() != null && post.getLikes().containsKey(currentUid)) {
            likesRef.child(currentUid).removeValue();
        } else {
            likesRef.child(currentUid).setValue(true);
        }
    }

    private void deletePost(String postId) {
        if (postId == null) {
            return;
        }
        FirebaseDatabase.getInstance().getReference("posts").child(postId).removeValue()
                .addOnSuccessListener(aVoid -> Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(context, "Failed to delete post", Toast.LENGTH_SHORT).show());
    }

    private String getFormattedTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60000) {
            return "Just now";
        } else if (diff < 3600000) {
            return (diff / 60000) + "m ago";
        } else if (diff < 86400000) {
            return (diff / 3600000) + "h ago";
        } else {
            return (diff / 86400000) + "d ago";
        }
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    @Override
    public void onViewRecycled(@NonNull PostFeedViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.exoPlayer != null) {
            holder.exoPlayer.release();
            holder.exoPlayer = null;
        }
    }

    public void playVideoAtPosition(int position, RecyclerView recyclerView) {
        if (position == playingPosition) {
            return;
        }

        if (playingPosition != -1 && currentlyPlayingProfile != null) {
            currentlyPlayingProfile.setPlayWhenReady(false);
        }

        PostFeedViewHolder holder = (PostFeedViewHolder) recyclerView.findViewHolderForAdapterPosition(position);
        if (holder != null && holder.exoPlayer != null) {
            holder.exoPlayer.setPlayWhenReady(true);
            currentlyPlayingProfile = holder.exoPlayer;
            playingPosition = position;
        } else {
            // Handle edge case where viewholder is currently being laid out or media is an image
            playingPosition = -1;
            currentlyPlayingProfile = null;
        }
    }

    public void pauseAll() {
        if (currentlyPlayingProfile != null) {
            currentlyPlayingProfile.setPlayWhenReady(false);
            playingPosition = -1;
            currentlyPlayingProfile = null;
        }
    }

    public void releaseAllPlayers(RecyclerView recyclerView) {
        pauseAll();
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
            if (holder instanceof PostFeedViewHolder) {
                PostFeedViewHolder pfHolder = (PostFeedViewHolder) holder;
                if (pfHolder.exoPlayer != null) {
                    pfHolder.exoPlayer.release();
                    pfHolder.exoPlayer = null;
                }
            }
        }
    }

    static class PostFeedViewHolder extends RecyclerView.ViewHolder {

        CircleImageView ivPostProfile;
        TextView tvPostUploader, tvPostTime, tvPostCaption, tvPostLikesCount;
        ImageButton btnPostDelete;
        ImageView ivPostImage, btnPostLike;
        PlayerView exoPlayerView;
        ExoPlayer exoPlayer;

        public PostFeedViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPostProfile = itemView.findViewById(R.id.ivPostProfile);
            tvPostUploader = itemView.findViewById(R.id.tvPostUploader);
            tvPostTime = itemView.findViewById(R.id.tvPostTime);
            tvPostCaption = itemView.findViewById(R.id.tvPostCaption);
            tvPostLikesCount = itemView.findViewById(R.id.tvPostLikesCount);
            btnPostDelete = itemView.findViewById(R.id.btnPostDelete);
            ivPostImage = itemView.findViewById(R.id.ivPostImage);
            btnPostLike = itemView.findViewById(R.id.btnPostLike);
            exoPlayerView = itemView.findViewById(R.id.exoPlayerView);
        }
    }
}
