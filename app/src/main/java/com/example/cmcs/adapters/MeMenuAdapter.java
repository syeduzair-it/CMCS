package com.example.cmcs.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.R;
import com.example.cmcs.models.MeMenuItem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter for the MeFragment menu list. Each row shows an icon, a
 * label, and a right-chevron arrow.
 */
public class MeMenuAdapter extends RecyclerView.Adapter<MeMenuAdapter.MenuViewHolder> {

    public interface OnItemClickListener {

        void onItemClick(MeMenuItem item);
    }

    private final List<MeMenuItem> items;
    private final OnItemClickListener listener;
    private final Set<String> badgeLabels = new HashSet<>();

    public MeMenuAdapter(List<MeMenuItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    /** Show or hide the unread dot for the item with the given label. */
    public void setBadge(String label, boolean show) {
        if (show) badgeLabels.add(label);
        else badgeLabels.remove(label);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getLabel().equals(label)) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public MenuViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_me_menu, parent, false);
        return new MenuViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MenuViewHolder holder, int position) {
        MeMenuItem item = items.get(position);
        holder.tvLabel.setText(item.getLabel());
        holder.ivIcon.setImageResource(item.getIconRes());
        holder.badgeDot.setVisibility(
                badgeLabels.contains(item.getLabel()) ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class MenuViewHolder extends RecyclerView.ViewHolder {

        final ImageView ivIcon;
        final TextView tvLabel;
        final View badgeDot;

        MenuViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivMenuIcon);
            tvLabel = itemView.findViewById(R.id.tvMenuLabel);
            badgeDot = itemView.findViewById(R.id.viewBadgeDot);
        }
    }
}
