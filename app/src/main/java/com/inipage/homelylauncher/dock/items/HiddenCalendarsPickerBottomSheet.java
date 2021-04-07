package com.inipage.homelylauncher.dock.items;

import android.content.Context;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.persistence.PrefsHelper;
import com.inipage.homelylauncher.utils.CalendarUtils;
import com.inipage.homelylauncher.views.BottomSheetHelper;

import java.util.List;

/**
 * Picker for choosing calendars to show in the dock. Backed with a SharedPreference.
 */
public class HiddenCalendarsPickerBottomSheet {

    public static void show(Context context) {
        List<CalendarUtils.Calendar> systemCalendars = CalendarUtils.getCalendars(context);
        final HiddenCalendarsAdapter adapter =
            new HiddenCalendarsAdapter(
                systemCalendars,
                modifiedList -> PrefsHelper.saveDisabledCalendars(context, modifiedList));
        final RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        new BottomSheetHelper()
            .setContentView(recyclerView)
            .setFixedScreenPercent(0.75F)
            .show(context, context.getString(R.string.choose_calendars_to_hide));
    }
}
