package com.inipage.homelylauncher.model;

import android.util.Log;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.grid.GridViewHolder;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Grid item that can be placed on different pages.
 */
public class ClassicGridItem extends GridItem {

    private String mPageId;

    public ClassicGridItem(
        String id,
        String pageId,
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
        super(id, x, y, width, height, type, gridFolder, DS1, DS2, DI);
        mPageId = pageId;
    }

    public void updatePageId(String pageId) {
        mPageId = pageId;
    }

    public String getPageId() {
        return mPageId;
    }

    @NotNull
    @Override
    public String toString() {
        return "pageId=" + getPageId() + " -- " + super.toString();
    }

    public static ClassicGridItem getNewWidgetItem(
        String pageId, int x, int y, int width, int height, int appWidgetID)
    {
        return new ClassicGridItem(
            UUID.randomUUID().toString(),
            pageId,
            x,
            y,
            width,
            height,
            GRID_TYPE_WIDGET,
            null,
            null,
            null,
            appWidgetID);
    }

    public static ClassicGridItem getNewAppItem(ApplicationIcon app) {
        return new ClassicGridItem(
            UUID.randomUUID().toString(),
            UNSET_STRING_ID,
            UNSET_ID,
            UNSET_ID,
            1,
            1,
            GRID_TYPE_APP,
            null,
            app.getPackageName(),
            app.getActivityName(),
            UNSET_ID);
    }
}
