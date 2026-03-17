package com.example.cmcs.adapters;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cmcs.AddStoryActivity;
import com.example.cmcs.R;
import com.example.cmcs.StoryViewerActivity;
import com.example.cmcs.models.StoryModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Horizontal stories strip adapter.
 *
 * View types: TYPE_ADD_STORY — "Your Story" upload button (teachers only)
 * TYPE_ADD_COLLEGE — "College Story" upload button (teachers only) TYPE_STORY —
 * actual story circle
 *
 * Teacher stories are GROUPED: one circle per teacher, thumbnail = latest
 * story, click launches all stories for that teacher in ASC timestamp order.
 *
 * College stories are also GROUPED: one circle for the college, thumbnail =
 * latest story, click launches all college stories in ASC timestamp order.
 */
public class StoryAdapter extends RecyclerView.Adapter<StoryAdapter.VH> {

    public static final int TYPE_ADD_STORY = 0;
    public static final int TYPE_ADD_COLLEGE = 1;
    public static final int TYPE_STORY = 2;

    /**
     * Internal item descriptor.
     */
    private static final class Item {

        final int type;
        /**
         * Thumbnail / label source. For teacher groups: the LAST (latest)
         * story. For college: the story itself. Null for upload buttons.
         */
        final StoryModel representative;
        /**
         * Full ordered list to pass to the viewer. Null for upload buttons.
         */
        final List<StoryModel> fullGroup;

        Item(int type, StoryModel rep, List<StoryModel> group) {
            this.type = type;
            this.representative = rep;
            this.fullGroup = group;
        }
    }

    private final Context ctx;
    private final List<Item> items = new ArrayList<>();

    /**
     * @param ctx Fragment/activity context.
     * @param storyGroups Map from group ID (teacherUid or "college_all") → list
     * of that group's stories (sorted ASC by timestamp). Iteration order must
     * be consistent (use LinkedHashMap).
     * @param isTeacher True → prepend upload buttons.
     */
    public StoryAdapter(Context ctx,
            Map<String, List<StoryModel>> storyGroups,
            boolean isTeacher) {
        this.ctx = ctx;

        if (isTeacher) {
            items.add(new Item(TYPE_ADD_STORY, null, null));
            items.add(new Item(TYPE_ADD_COLLEGE, null, null));
        }

        // Add story groups — one circle per group, thumbnail = last (latest) story
        for (Map.Entry<String, List<StoryModel>> entry : storyGroups.entrySet()) {
            List<StoryModel> group = entry.getValue();
            if (group.isEmpty()) {
                continue;
            }
            StoryModel representative = group.get(group.size() - 1); // latest thumbnail
            items.add(new Item(TYPE_STORY, representative, new ArrayList<>(group)));
        }
    }

    // ── RecyclerView boilerplate ────────────────────────────────────────────
    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx)
                .inflate(R.layout.item_story, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item item = items.get(position);

        if (item.type == TYPE_ADD_STORY) {
            h.tvLabel.setText("Your Story");
            h.tvAddBadge.setVisibility(View.VISIBLE);
            h.storyRing.setVisibility(View.INVISIBLE);
            Glide.with(ctx).load(R.drawable.ic_teacher).circleCrop().into(h.ivThumb);
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(ctx, AddStoryActivity.class);
                i.putExtra(AddStoryActivity.EXTRA_TYPE, "teacher");
                ctx.startActivity(i);
            });
            return;
        }

        if (item.type == TYPE_ADD_COLLEGE) {
            h.tvLabel.setText("College Story");
            h.tvAddBadge.setVisibility(View.VISIBLE);
            h.storyRing.setVisibility(View.INVISIBLE);
            Glide.with(ctx).load(R.drawable.cmcs_logo_symbol).circleCrop().into(h.ivThumb);
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(ctx, AddStoryActivity.class);
                i.putExtra(AddStoryActivity.EXTRA_TYPE, "college");
                ctx.startActivity(i);
            });
            return;
        }

        // TYPE_STORY
        StoryModel rep = item.representative;
        h.tvAddBadge.setVisibility(View.GONE);
        h.storyRing.setVisibility(View.VISIBLE);

        // LABEL logic:
        // College stories -> "CMCS"
        // Teacher stories -> Teacher name (fallback to "Story")
        String label;
        if ("college".equals(rep.getType())) {
            label = "CMCS";
        } else {
            label = !TextUtils.isEmpty(rep.getTeacherName()) ? rep.getTeacherName() : "Story";
        }
        h.tvLabel.setText(label);

        // Thumbnail — use the representative (latest story in group)
        Glide.with(ctx)
                .load(rep.getMediaUrl())
                .thumbnail(0.3f)
                .placeholder(R.color.surfaceElevated)
                .circleCrop()
                .into(h.ivThumb);

        // Launch viewer with the full group, starting at index 0
        h.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(ctx, StoryViewerActivity.class);
            intent.putParcelableArrayListExtra(
                    StoryViewerActivity.EXTRA_STORIES,
                    new ArrayList<>(item.fullGroup));
            intent.putExtra(StoryViewerActivity.EXTRA_START_INDEX, 0);
            ctx.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ── ViewHolder ─────────────────────────────────────────────────────────
    static class VH extends RecyclerView.ViewHolder {

        CircleImageView ivThumb;
        TextView tvLabel;
        TextView tvAddBadge;
        View storyRing;

        VH(@NonNull View v) {
            super(v);
            ivThumb = v.findViewById(R.id.ivStoryThumb);
            tvLabel = v.findViewById(R.id.tvStoryLabel);
            tvAddBadge = v.findViewById(R.id.tvAddBadge);
            storyRing = v.findViewById(R.id.storyRing);
        }
    }
}
