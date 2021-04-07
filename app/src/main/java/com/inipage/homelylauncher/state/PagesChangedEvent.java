package com.inipage.homelylauncher.state;

public class PagesChangedEvent {

    private final int mNewPageCount;

    public PagesChangedEvent(int newPageCount) {
        mNewPageCount = newPageCount;
    }

    public int getNewPageCount() {
        return mNewPageCount;
    }
}
