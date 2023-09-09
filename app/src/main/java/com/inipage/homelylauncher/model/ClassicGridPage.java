package com.inipage.homelylauncher.model;

import com.inipage.homelylauncher.grid.GridMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Conventional (page style) grid pages.
 */
public class ClassicGridPage extends GridPage<ClassicGridItem> {

    private final String mId;
    private final int mIndex;

    public ClassicGridPage(List<ClassicGridItem> items, String id, int index, int width, int height) {
        super(items, width, height);
        mId = id;
        mIndex = index;
    }

    public static ClassicGridPage getInitialPage() {
        return new ClassicGridPage(
            new ArrayList<>(),
            UUID.randomUUID().toString(),
            0,
            DIMENSION_UNSET,
            DIMENSION_UNSET);
    }

    public static ClassicGridPage spawnNewPage(ClassicGridPage template) {
        return new ClassicGridPage(
            new ArrayList<>(),
            UUID.randomUUID().toString(),
            template.getIndex() + 1,
            template.getWidth(),
            template.getHeight());
    }

    public int getIndex() {
        return mIndex;
    }

    public String getID() {
        return mId;
    }

    @Override
    public void sizeFromContainer(GridMetrics gridMetrics) {
        mHeight = gridMetrics.getRowCount();
        mWidth = gridMetrics.getColumnCount();
        mItems = new ArrayList<>();
    }
}
