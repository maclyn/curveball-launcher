package com.inipage.homelylauncher.dock;

import android.content.Context;
import android.view.View;

import com.inipage.homelylauncher.model.DockItem;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.utils.InstalledAppUtils;

import java.util.Map;

public abstract class ConfigurableAppBackedDockItem implements DockControllerItem {

    private final DockItem mBackingItem;

    public ConfigurableAppBackedDockItem(Map<Integer, DockItem> items) {
        if (items.containsKey(getDatabaseField())) {
            mBackingItem = items.get(getDatabaseField());
        } else {
            mBackingItem = DockItem.createUnsetContextualItem(getDatabaseField());
        }
    }

    protected abstract int getDatabaseField();

    @Override
    public Runnable getAction(View view, Context context) {
        return () -> {
            if (mBackingItem.hasValidComponent()) {
                int[] out = new int[2];
                view.getLocationOnScreen(out);
                final boolean success = InstalledAppUtils.launchApp(
                    out[0],
                    out[1],
                    view.getWidth(),
                    view.getWidth(),
                    view.getContext(),
                    mBackingItem.getPackageName(),
                    mBackingItem.getActivityName());
                if (success) {
                    return;
                }
                mBackingItem.unsetComponent();
                DatabaseEditor.get().addDockItem(mBackingItem);
            }
            new ActivityPickerBottomSheet(
                context,
                (packageName, activityName) -> {
                    mBackingItem.setComponent(packageName, activityName);
                    DatabaseEditor.get().addDockItem(mBackingItem);
                },
                context.getString(getBottomSheetMessage()));
        };
    }

    protected abstract int getBottomSheetMessage();
}
