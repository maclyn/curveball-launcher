package com.inipage.homelylauncher.state;

import org.greenrobot.eventbus.EventBus;

public class LayoutEditingSingleton {

    private static LayoutEditingSingleton s_INSTANCE;

    private boolean mIsEditing;

    private LayoutEditingSingleton() {
        mIsEditing = false;
    }

    public static LayoutEditingSingleton getInstance() {
        if (s_INSTANCE == null) {
            s_INSTANCE = new LayoutEditingSingleton();
        }
        return s_INSTANCE;
    }

    public boolean isEditing() {
        return mIsEditing;
    }

    public void setEditing(boolean isEditing) {
        if (mIsEditing == isEditing) {
            return;
        }
        mIsEditing = isEditing;
        EventBus.getDefault().post(new EditingEvent(isEditing));
    }
}
