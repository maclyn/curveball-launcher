package com.inipage.homelylauncher.model;

import com.inipage.homelylauncher.grid.GridMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class GridPage<GridItemType> {

    protected static final int DIMENSION_UNSET = -1;

    protected List<GridItemType> mItems;
    protected int mWidth;
    protected int mHeight;

    public GridPage(List<GridItemType> items, int width, int height) {
        mItems = items;
        mWidth = width;
        mHeight = height;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public List<GridItemType> getItems() {
        return mItems;
    }

    public boolean areDimensionsUnset() {
        return mWidth == DIMENSION_UNSET;
    }

    /**
     * Set width and height of this page's grid based on the container size.
     */
    public abstract void sizeFromContainer(GridMetrics gridMetrics);
}
