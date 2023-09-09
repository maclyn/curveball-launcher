package com.inipage.homelylauncher.model;

import com.inipage.homelylauncher.grid.GridMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vertical (infinite scroll) grid page.
 */
public class VerticalGridPage extends GridPage<GridItem> {

    public VerticalGridPage(List<GridItem> items, int width, int height) {
        super(items, width, height);
    }

    public static VerticalGridPage getInitialPage() {
        return new VerticalGridPage(
            new ArrayList<>(),
            DIMENSION_UNSET,
            DIMENSION_UNSET);
    }

    @Override
    public void sizeFromContainer(GridMetrics gridMetrics) {
        mHeight = 1;
        mWidth = gridMetrics.getRowCount();
        mItems = new ArrayList<>();
    }
}
