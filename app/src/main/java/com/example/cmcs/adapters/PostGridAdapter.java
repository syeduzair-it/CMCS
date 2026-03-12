package com.example.cmcs.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cmcs.AddPostActivity;
import com.example.cmcs.PostFeedActivity;
import com.example.cmcs.R;
import com.example.cmcs.models.PostModel;

import java.util.List;

public class PostGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ADD_POST = 0;
    private static final int TYPE_POST = 1;

    private Context context;
    private List<PostModel> postList;
    private boolean isTeacher;

    public PostGridAdapter(Context context, List<PostModel> postList, boolean isTeacher) {
        this.context = context;
        this.postList = postList;
        this.isTeacher = isTeacher;
    }

    @Override
    public int getItemViewType(int position) {
        if (isTeacher && position == 0) {
            return TYPE_ADD_POST;
        }
        return TYPE_POST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ADD_POST) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_post_grid_add, parent, false);
            return new AddPostViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_post_grid, parent, false);
            return new PostViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == TYPE_ADD_POST) {
            holder.itemView.setOnClickListener(v -> {
                context.startActivity(new Intent(context, AddPostActivity.class));
            });
        } else {
            int actualPos = isTeacher ? position - 1 : position;
            PostModel post = postList.get(actualPos);
            PostViewHolder postHolder = (PostViewHolder) holder;

            Glide.with(context)
                    .load(post.getMediaUrl())
                    .centerCrop()
                    .into(postHolder.ivGridThumbnail);

            if ("video".equals(post.getMediaType())) {
                postHolder.ivVideoIcon.setVisibility(View.VISIBLE);
            } else {
                postHolder.ivVideoIcon.setVisibility(View.GONE);
            }

            postHolder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, PostFeedActivity.class);
                intent.putExtra("scrollToPostId", post.getPostId());
                context.startActivity(intent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return isTeacher ? postList.size() + 1 : postList.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {

        ImageView ivGridThumbnail, ivVideoIcon;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGridThumbnail = itemView.findViewById(R.id.ivGridThumbnail);
            ivVideoIcon = itemView.findViewById(R.id.ivVideoIcon);
        }
    }

    static class AddPostViewHolder extends RecyclerView.ViewHolder {

        public AddPostViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
