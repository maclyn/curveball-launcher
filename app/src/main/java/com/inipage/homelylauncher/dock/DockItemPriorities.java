package com.inipage.homelylauncher.dock;

public enum DockItemPriorities {
    
    PRIORITY_CALL(1000),
    PRIORITY_EVENT_RANGED(990),
    PRIORITY_ALARM(950),
    PRIORITY_POWER_EVENT_LOW(500),
    PRIORITY_EVENT_ALL_DAY(400),
    PRIORITY_WEATHER(350),
    PRIORITY_POWER_EVENT_CHARGING(300),
    PRIORITY_RECENT_APP(0);

    private final int mValue;

    DockItemPriorities(int value) {
        mValue = value;
    }

    public int getPriority() {
        return mValue;
    }
}
