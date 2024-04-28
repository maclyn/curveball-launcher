package com.inipage.homelylauncher.model;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.grid.GridViewHolder;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Grid item. {@linkplain ClassicGridItem} adds page IDs to this class for use in conventional
 * multi-page setups.
 */
public class GridItem {

    public static final int GRID_TYPE_APP = 1; /* Package name, activity name {str@2} */
    public static final int GRID_TYPE_WIDGET = 2; /* Widget ID {int@1} */

    // Let the DB autoincrement this
    static final String UNSET_STRING_ID = "unset";
    static final int UNSET_ID = Integer.MIN_VALUE;

    private final String mID;
    private final int mType;
    @Nullable GridFolder mGridFolder;
    @Nullable private final String mDS1;
    @Nullable private final String mDS2;
    private final int mDI;

    // These fields can change as GridItems are moved and resized
    private int mX;
    private int mY;
    private int mWidth;
    private int mHeight;

    public GridItem(
        String id,
        int x,
        int y,
        int width,
        int height,
        int type,
        @Nullable GridFolder gridFolder,
        @Nullable String DS1,
        @Nullable String DS2,
        int DI)
    {
        mID = id;
        mX = x;
        mY = y;
        mWidth = width;
        mHeight = height;
        mType = type;
        mGridFolder = gridFolder;
        mDS1 = DS1;
        mDS2 = DS2;
        mDI = DI;
    }

    public boolean isSizeUnset() {
        return mX == UNSET_ID;
    }

    public String getPackageName() {
        if (mType != GRID_TYPE_APP) {
            throw new IllegalArgumentException();
        }
        return mDS1;
    }

    public String getActivityName() {
        if (mType != GRID_TYPE_APP) {
            throw new IllegalArgumentException();
        }
        return mDS2;
    }

    public int getWidgetID() {
        if (mType != GRID_TYPE_WIDGET) {
            throw new IllegalArgumentException();
        }
        return mDI;
    }

    public void resize(GridViewHolder.ResizeDirection direction) {
        switch (direction) {
            case UP:
                mY -= 1;
                mHeight += 1;
                break;
            case DOWN:
                mHeight += 1;
                break;
            case LEFT:
                mX -= 1;
                mWidth += 1;
                break;
            case RIGHT:
                mWidth += 1;
                break;
            case UP_IN:
                mY += 1;
                mHeight -= 1;
                break;
            case DOWN_IN:
                mHeight -= 1;
                break;
            case LEFT_IN:
                mX += 1;
                mWidth -= 1;
                break;
            case RIGHT_IN:
                mWidth -= 1;
                break;
        }
    }

    public void update(int x, int y) {
        mX = x;
        mY = y;
    }

    // This isn't quite equals
    public boolean equalish(@Nullable Object obj) {
        if (!(obj instanceof ClassicGridItem)) {
            return false;
        }
        ClassicGridItem other = (ClassicGridItem) obj;
        return other.getID().equals(getID()) &&
            other.getType() == getType() &&
            other.getX() == getX() &&
            other.getY() == getY() &&
            other.getWidth() == getWidth() &&
            other.getHeight() == getHeight();
    }

    public String getID() {
        return mID;
    }

    public int getType() {
        return mType;
    }

    public int getX() {
        return mX;
    }

    public int getY() {
        return mY;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    @Nullable
    public GridFolder getGridFolder() {
        return mGridFolder;
    }

    @Nullable
    public String getDS1() {
        return mDS1;
    }

    @Nullable
    public String getDS2() {
        return mDS2;
    }

    public int getDI() {
        return mDI;
    }

    @NotNull
    @Override
    public String toString() {
        return getX() + "x" + getY() +
            "-- Type=" + getType() +
            "-- DS=" + getDS1() + ", " + getDS2() + ", " + getDI();
    }
}
