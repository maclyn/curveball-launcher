package com.inipage.homelylauncher.state;

public class EditingEvent {

    private final boolean mIsEditing;

    EditingEvent(boolean isEditing) {
        mIsEditing = isEditing;
    }

    public boolean isEditing() {
        return mIsEditing;
    }
}
