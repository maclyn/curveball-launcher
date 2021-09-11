package com.inipage.homelylauncher.grid;

import android.appwidget.AppWidgetProviderInfo;

import com.inipage.homelylauncher.utils.Constants;

public class GridMetrics {

    private final int mCellWidth;
    private final int mCellHeight;
    private final int mColumnCount;
    private final int mRowCount;

    public GridMetrics(int screenHeight, int screenWidth, int columnCount) {
        float scalar = chooseScalar(screenWidth, screenHeight);
        mCellWidth = screenWidth / columnCount;
        mCellHeight = (int) (mCellWidth * scalar);
        mColumnCount = columnCount;
        mRowCount = Math.min(
            Constants.DEFAULT_MAX_ROW_COUNT,
            (int) Math.floor(screenHeight / (float) mCellHeight));
    }

    public GridMetrics(int rowCount, int columnCount, int screenHeight, int screenWidth) {
        float scalar = chooseScalar(screenWidth, screenHeight);
        mRowCount = rowCount;
        mColumnCount = columnCount;
        // So we *could* be in a bit of a pickle at this point! Make sure cellSize doesn't cause
        // columns or rows to end up offscreen, since when we restore the GridPageController, the
        // total screen height could be different (e.g. switching from nav controls <-> buttons)
        int hopefulCellSize = screenWidth / columnCount;
        if ((hopefulCellSize * scalar * rowCount) < screenHeight) {
            mCellWidth = hopefulCellSize;
        } else {
            // We're actually height constrained
            mCellWidth = (int) (screenHeight / rowCount / scalar);
        }
        mCellHeight = (int) (mCellWidth * scalar);
    }

    public int getColumnCount() {
        return mColumnCount;
    }

    public int getRowCount() {
        return mRowCount;
    }

    public int getCellWidthPx() {
        return mCellWidth;
    }

    public int getCellHeightPx() {
        return mCellHeight;
    }

    public int getWidthOfColumnSpanPx(int span) {
        return (mCellWidth * span);
    }

    public int getHeightOfRowSpanPx(int span) {
        return (mCellHeight * span);
    }

    public int getMinColumnCountForWidget(AppWidgetProviderInfo awpi) {
        int minWidth = awpi.minWidth;
        if (minWidth < mCellWidth) {
            minWidth = mCellWidth;
        }
        int columnsToSpan = (int) Math.ceil((float) minWidth / mCellWidth);
        if (columnsToSpan > mColumnCount) {
            columnsToSpan = mColumnCount;
        }
        return columnsToSpan;
    }

    public int getMinRowCountForWidget(AppWidgetProviderInfo awpi) {
        int minHeight = awpi.minHeight;
        if (minHeight < mCellHeight) {
            minHeight = mCellHeight;
        }
        return (int) Math.ceil((float) minHeight / mCellHeight);
    }

    private float chooseScalar(int width, int height) {
        return height < width ?
               Constants.SQUAT_WIDTH_TO_HEIGHT_SCALAR :
               Constants.DEFAULT_WIDTH_TO_HEIGHT_SCALAR;
    }
}
