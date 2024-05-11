package com.inipage.homelylauncher.dock;

public enum DockItemPriorities {
    
    PRIORITY_CALL(1000),
    PRIORITY_ALARM(950),
    PRIORITY_EVENT(900),
    PRIORITY_POWER_EVENT(300),
    PRIORITY_RECENT_APP(0);

    private final int mValue;

    DockItemPriorities(int value) {
        mValue = value;
    }

    public int getPriority() {
        return mValue;
    }
}
