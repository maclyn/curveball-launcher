package com.inipage.homelylauncher.swipefolders;

import android.util.Pair;
import android.view.View;

import com.google.common.collect.ImmutableList;
import com.inipage.homelylauncher.grid.AppViewHolder;
import com.inipage.homelylauncher.model.SwipeApp;
import com.inipage.homelylauncher.model.SwipeFolder;
import com.inipage.homelylauncher.views.DecorViewDragger.DragEvent;

import java.util.List;

import static android.view.DragEvent.ACTION_DRAG_ENDED;
import static android.view.DragEvent.ACTION_DRAG_ENTERED;
import static android.view.DragEvent.ACTION_DRAG_EXITED;
import static android.view.DragEvent.ACTION_DRAG_LOCATION;
import static android.view.DragEvent.ACTION_DRAG_STARTED;
import static android.view.DragEvent.ACTION_DROP;

/**
 * Translate drag events into different actions.
 */
class PerDragGestureValues {


    public static final int NO_ITEM_UNDER_DRAG = -1;

    private final ImmutableList<SwipeFolder> mShortcuts;
    private final AppViewHolder mAppViewHolder;
    private final SwipeApp mSwipeApp;
    private final int mElementCount;
    private final boolean mShowingAdditionOption;

    // Calculated values
    private final float mFolderWidth;
    private final int mAdditionIndex;
    private final List<Pair<Float, Float>> mFolderDragRanges;

    private int mFolderSelected;

    PerDragGestureValues(
        View view,
        ImmutableList<SwipeFolder> shortcuts,
        DragEvent initialEvent,
        SwipeAttributes sharedAttrs) {
        mShortcuts = shortcuts;
        mAppViewHolder = (AppViewHolder) initialEvent.getLocalState();
        mSwipeApp =
            new SwipeApp(
                mAppViewHolder.getItem().getPackageName(),
                mAppViewHolder.getItem().getActivityName());
        mFolderSelected = NO_ITEM_UNDER_DRAG;
        mShowingAdditionOption = shortcuts.size() < SwipeAttributes.MAXIMUM_ELEMENTS_PER_ROW;
        mElementCount = (shortcuts.size() + (mShowingAdditionOption ? 1 : 0));
        final float totalSpaceSize = view.getWidth();
        mFolderWidth = totalSpaceSize / mElementCount;
        final ImmutableList.Builder<Pair<Float, Float>> folderDragRangesBuilder =
            new ImmutableList.Builder<>();
        float startDragWidthX = (view.getWidth() - (mFolderWidth * mElementCount)) / 2F;
        for (int i = 0; i < mElementCount; i++) {
            folderDragRangesBuilder.add(
                new Pair<>(startDragWidthX, startDragWidthX + mFolderWidth));
            startDragWidthX += mFolderWidth;
        }
        mAdditionIndex =
            mShowingAdditionOption ?
            (mElementCount - 1) :
            NO_ITEM_UNDER_DRAG;
        mFolderDragRanges = folderDragRangesBuilder.build();
    }

    public boolean update(
        View view,
        DragEvent dragEvent,
        SwipeAttributes sharedAttrs) {
        if (dragEvent.getAction() == ACTION_DRAG_EXITED) {
            // x/y will be 0, but that's not valid
            mFolderSelected = NO_ITEM_UNDER_DRAG;
            return false;
        }

        float xValue;
        switch (dragEvent.getAction()) {
            case ACTION_DRAG_ENTERED:
            case ACTION_DRAG_LOCATION:
                xValue = dragEvent.getX(view);
                break;
            case ACTION_DRAG_STARTED:
            case ACTION_DRAG_ENDED:
            case ACTION_DROP:
            default:
                return false;
        }
        int selectedFolder = NO_ITEM_UNDER_DRAG;
        for (int i = 0; i < mFolderDragRanges.size(); i++) {
            Pair<Float, Float> range = mFolderDragRanges.get(i);
            if (xValue >= range.first && xValue <= range.second) {
                selectedFolder = i;
                break;
            }
        }
        boolean shouldBuzz = false;
        if (selectedFolder != mFolderSelected) {
            shouldBuzz = true;
        }
        mFolderSelected = selectedFolder;
        return shouldBuzz;
    }

    public ImmutableList<SwipeFolder> getShortcuts() {
        return mShortcuts;
    }

    public List<Pair<Float, Float>> getFolderRanges() {
        return mFolderDragRanges;
    }

    public float getFolderWidth() {
        return mFolderWidth;
    }

    public AppViewHolder getAppViewHolder() {
        return mAppViewHolder;
    }

    public SwipeApp getShortcutApp() {
        return mSwipeApp;
    }

    public int getSelectedFolder() {
        return mFolderSelected;
    }
}