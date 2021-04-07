package com.inipage.homelylauncher.model;

import android.util.Log;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.grid.GridViewHolder;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class GridItem {

    public static final int GRID_TYPE_APP = 1; /* Package name, activity name {str@2} */
    public static final int GRID_TYPE_WIDGET = 2; /* Widget ID {int@1} */
    public static final int GRID_TYPE_SHORTCUT = 3; /* TODO: Implement shortcuts */
    // Let the DB autoincrement this
    static final String UNSET_STRING_ID = "unset";
    static final int UNSET_ID = Integer.MIN_VALUE;
    private final String mID;
    private final int mType;
    private final String mDS1;
    private final String mDS2;
    private final int mDI;

    // These fields can change as GridItems are moved and resized
    private int mX;
    private int mY;
    private String mPageId;
    private int mWidth;
    private int mHeight;

    public GridItem(
        String id,
        String pageId,
        int x,
        int y,
        int width,
        int height,
        int type,
        String DS1,
        String DS2,
        int DI) {
        mID = id;
        mX = x;
        mY = y;
        mPageId = pageId;
        mWidth = width;
        mHeight = height;
        mType = type;
        mDS1 = DS1;
        mDS2 = DS2;
        mDI = DI;
    }

    public static GridItem getNewWidgetItem(
        String pageId, int x, int y, int width, int height, int appWidgetID) {
        return new GridItem(
            UUID.randomUUID().toString(),
            pageId,
            x,
            y,
            width,
            height,
            GRID_TYPE_WIDGET,
            null,
            null,
            appWidgetID);
    }

    public static GridItem getNewAppItem(ApplicationIcon app) {
        return new GridItem(
            UUID.randomUUID().toString(),
            UNSET_STRING_ID,
            UNSET_ID,
            UNSET_ID,
            1,
            1,
            GRID_TYPE_APP,
            app.getPackageName(),
            app.getActivityName(),
            UNSET_ID);
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
        Log.d("MACLYN", "Resizing item in direction = " + direction);
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

    public void update(String pageID, int x, int y) {
        mPageId = pageID;
        mX = x;
        mY = y;
    }

    // This isn't quite equals
    public boolean equalish(@Nullable Object obj) {
        if (!(obj instanceof GridItem)) {
            return false;
        }
        GridItem other = (GridItem) obj;
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

    @NotNull
    @Override
    public String toString() {
        return getX() + "x" + getY() + " on " + getPageId() + "-- Type=" + getType() + " DS=" +
            getDS1() + ", " + getDS2() + ", " + getDI();
    }

    public String getPageId() {
        return mPageId;
    }

    public String getDS1() {
        return mDS1;
    }

    public String getDS2() {
        return mDS2;
    }

    public int getDI() {
        return mDI;
    }
}
