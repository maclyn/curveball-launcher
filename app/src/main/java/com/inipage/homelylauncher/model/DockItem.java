package com.inipage.homelylauncher.model;

public class DockItem {

    public static final int DOCK_SHOW_NEVER = 1;

    public static final int DOCK_SHOW_IN_CALL = 2;
    public static final int DOCK_SHOW_IN_LOW_POWER = 5;

    private static final String UNSET_STRING_FIELD = "unset";
    private static final int UNSET_INT_FIELD = -1;


    private final int mWhenToShow;
    private String mPackageName;
    private String mActivityName;

    public DockItem(String packageName, String activityName, int whenToShow) {
        this.mPackageName = packageName;
        this.mActivityName = activityName;
        this.mWhenToShow = whenToShow;
    }

    public static DockItem createUnsetContextualItem(int whenToShow) {
        return new DockItem(
            UNSET_STRING_FIELD, UNSET_STRING_FIELD, whenToShow);
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getActivityName() {
        return mActivityName;
    }

    public boolean hasValidComponent() {
        return !mPackageName.equals(UNSET_STRING_FIELD);
    }

    public void setComponent(String packageName, String activityName) {
        this.mPackageName = packageName;
        this.mActivityName = activityName;
    }

    public void unsetComponent() {
        this.mPackageName = this.mActivityName = UNSET_STRING_FIELD;
    }

    public boolean isHidden() {
        return mWhenToShow == DOCK_SHOW_NEVER;
    }

    public int getWhenToShow() {
        return mWhenToShow;
    }

    public String lookupKey() {
        return mPackageName + "|" + mActivityName;
    }
}
