package com.inipage.homelylauncher.dock.items;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.dock.ConfigurableAppBackedDockItem;
import com.inipage.homelylauncher.dock.DockItemPriorities;
import com.inipage.homelylauncher.model.DockItem;
import com.inipage.homelylauncher.utils.BatteryUtils;
import com.inipage.homelylauncher.utils.PhoneUtils;

import java.util.Map;

public class PhoneMappedDockItem extends ConfigurableAppBackedDockItem {

    @Nullable private BroadcastReceiver mBroadcastReceiver;

    public PhoneMappedDockItem(Map<Integer, DockItem> appMap) {
        super(appMap);
    }

    @Override
    public void onAttach() {
        @Nullable final Context context = getContext();
        if (context == null) {
            return;
        }
        onCallStatusChanged();
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onCallStatusChanged();
            }
        };
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        context.registerReceiver(mBroadcastReceiver, filter);
    }

    private void onCallStatusChanged() {
        @Nullable final Context context = getContext();
        if (context == null) {
            return;
        }
        if (PhoneUtils.isInCall(context)) {
            mHost.showHostedItem();
        } else {
            mHost.hideHostedItem();
        }
    }

    @Override
    public void onDetach() {
        @Nullable final Context context = getContext();
        if (context == null || mBroadcastReceiver == null) {
            return;
        }
        context.unregisterReceiver(mBroadcastReceiver);
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
    public int getIcon() {
        return R.drawable.dock_icon_call;
    }

    @Override
    public int getTint() {
        @Nullable final Context context = getContext();
        if (context == null) {
            return super.getTint();
        }
        return context.getColor(R.color.dock_item_call_color);
    }

    @Override
    public long getBasePriority() {
        return DockItemPriorities.PRIORITY_CALL.getPriority();
    }
}
