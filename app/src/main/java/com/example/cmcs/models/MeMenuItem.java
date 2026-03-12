package com.example.cmcs.models;

/**
 * Represents a single row item in the MeFragment RecyclerView menu.
 */
public class MeMenuItem {

    private final String label;
    private final int iconRes;

    public MeMenuItem(String label, int iconRes) {
        this.label = label;
        this.iconRes = iconRes;
    }

    public String getLabel() {
        return label;
    }

    public int getIconRes() {
        return iconRes;
    }
}
