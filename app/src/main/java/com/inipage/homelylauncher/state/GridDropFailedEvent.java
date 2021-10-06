package com.inipage.homelylauncher.state;

import com.inipage.homelylauncher.grid.GridViewHolder;

/**
 * An event posted when a drop on the grid fails.
 */
public class GridDropFailedEvent {

    private final GridViewHolder mGridViewHolder;

    public GridDropFailedEvent(GridViewHolder holder) {
        mGridViewHolder = holder;
    }

    public GridViewHolder getGridViewHolder() {
        return mGridViewHolder;
    }
}
