package com.inipage.homelylauncher.model;

import com.inipage.homelylauncher.grid.GridMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GridPage {

    private static final int DIMENSION_UNSET = -1;
    private final String mId;
    private final int mIndex;
    private List<GridItem> mItems;
    private int mWidth;
    private int mHeight;

    public GridPage(List<GridItem> items, String id, int index, int width, int height) {
        mId = id;
        mItems = items;
        mIndex = index;
        mWidth = width;
        mHeight = height;
    }

    public static GridPage getInitialPage() {
        return new GridPage(
            new ArrayList<>(),
            UUID.randomUUID().toString(),
            0,
            DIMENSION_UNSET,
            DIMENSION_UNSET);
    }

    public static GridPage spawnNewPage(GridPage template) {
        return new GridPage(
            new ArrayList<>(),
            UUID.randomUUID().toString(),
            template.getIndex() + 1,
            template.getWidth(),
            template.getHeight());
    }

    public int getIndex() {
        return mIndex;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public String getID() {
        return mId;
    }

    public List<GridItem> getItems() {
        return mItems;
    }

    public boolean areDimensionsUnset() {
        return mWidth == DIMENSION_UNSET;
    }

    /**
     * Set width and height of this page's grid based on the container size.
     */
    public void sizeFromContainer(GridMetrics gridMetrics) {
        mHeight = gridMetrics.getRowCount();
        mWidth = gridMetrics.getColumnCount();
        mItems = new ArrayList<>();
    }
}
