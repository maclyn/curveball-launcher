package com.inipage.homelylauncher.state;

/**
 * An event posted when the page count changes.
 */
public class PagesChangedEvent {

    private final int mNewPageCount;

    public PagesChangedEvent(int newPageCount) {
        mNewPageCount = newPageCount;
    }

    public int getNewPageCount() {
        return mNewPageCount;
    }
}
