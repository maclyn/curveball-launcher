package com.inipage.homelylauncher.dock.items;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.dock.DockControllerItem;
import com.inipage.homelylauncher.dock.DockItemPriorities;
import com.inipage.homelylauncher.utils.CalendarUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CalendarMappedDockItem implements DockControllerItem {

    private static final SimpleDateFormat EVENT_DATE_FORMATTER = new SimpleDateFormat(
        "h:mm aa",
        Locale.getDefault());
    private static final long ONE_DAY_MS = 1000 * 60 * 60 * 24;

    @Nullable
    private final CalendarUtils.Event mEvent;

    public CalendarMappedDockItem(Context context) {
        CalendarUtils.Event event = CalendarUtils.findRelevantEvent(context);
        if (event != null && event.getAllDay() &&
            (event.getStart() - event.getEnd() > ONE_DAY_MS)) {
            event = null;
        }
        mEvent = event;
    }

    @Override
    public boolean isActive(Context context) {
        return mEvent != null;
    }

    @Override
    public int getIcon() {
        return R.drawable.dock_icon_event;
    }

    @Nullable
    @Override
    public String getLabel(Context context) {
        return mEvent == null ? null : mEvent.getTitle();
    }

    @Nullable
    @Override
    public String getSecondaryLabel(Context context) {
        if (mEvent == null) {
            return null;
        }
        return mEvent.getAllDay() ?
               context.getString(R.string.all_day) :
               EVENT_DATE_FORMATTER.format(new Date(mEvent.getStart()));
    }

    @Override
    public int getTint(Context context, Callback __) {
        return context.getColor(R.color.dock_item_calendar_color);
    }

    @Override
    public Runnable getAction(View view, Context context) {
        return () -> CalendarUtils.launchEvent(context, mEvent == null ? -1 : mEvent.getID());
    }

    @Nullable
    @Override
    public Runnable getSecondaryAction(View view, Context context) {
        return () -> HiddenCalendarsPickerBottomSheet.show(context);
    }

    @Override
    public long getBasePriority() {
        return mEvent.getAllDay() ?
               DockItemPriorities.PRIORITY_EVENT_ALL_DAY :
               DockItemPriorities.PRIORITY_EVENT_RANGED;
    }
}
