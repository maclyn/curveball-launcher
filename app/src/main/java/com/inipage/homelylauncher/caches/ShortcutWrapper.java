package com.inipage.homelylauncher.caches;

import android.content.pm.ShortcutInfo;
import android.text.TextUtils;

public class ShortcutWrapper {

    private final ShortcutInfo mShortcutInfo;
    private final String mLabel;

    public ShortcutWrapper(ShortcutInfo shortcutInfo) {
        this.mShortcutInfo = shortcutInfo;
        if (!TextUtils.isEmpty(shortcutInfo.getShortLabel())) {
            mLabel = shortcutInfo.getShortLabel().toString();
        } else if (!TextUtils.isEmpty(shortcutInfo.getLongLabel())) {
            mLabel = shortcutInfo.getLongLabel().toString();
        } else {
            mLabel = mShortcutInfo.getPackage();
        }
    }

    public ShortcutInfo getShortcutInfo() {
        return mShortcutInfo;
    }

    public String getLabel() {
        return mLabel;
    }
}
