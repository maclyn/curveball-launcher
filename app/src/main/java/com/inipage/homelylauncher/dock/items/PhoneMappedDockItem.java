package com.inipage.homelylauncher.dock.items;

import android.content.Context;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.dock.ConfigurableAppBackedDockItem;
import com.inipage.homelylauncher.dock.DockItemPriorities;
import com.inipage.homelylauncher.model.DockItem;
import com.inipage.homelylauncher.utils.PhoneUtils;

import java.util.Map;

public class PhoneMappedDockItem extends ConfigurableAppBackedDockItem {

    public PhoneMappedDockItem(Context context, Map<Integer, DockItem> appMap) {
        super(appMap);
    }

    @Override
    protected int getDatabaseField() {
        return DockItem.DOCK_SHOW_IN_CALL;
    }

    @Override
    protected int getBottomSheetMessage() {
        return R.string.dock_show_in_call;
    }

    @Override
    public boolean isActive(Context context) {
        return PhoneUtils.isInCall(context);
    }

    @Override
    public int getIcon() {
        return R.drawable.dock_icon_call;
    }

    @Override
    public int getTint(Context context, TintCallback __) {
        return context.getColor(R.color.dock_item_call_color);
    }

    @Override
    public long getBasePriority() {
        return DockItemPriorities.PRIORITY_CALL.getPriority();
    }
}
