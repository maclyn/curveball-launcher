package com.inipage.homelylauncher.widgets;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.util.Pair;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.grid.GridMetrics;
import com.inipage.homelylauncher.views.BottomSheetHelper;
import com.inipage.homelylauncher.views.DecorViewManager;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WidgetAddBottomSheet {

    public static void show(
        Context context,
        int targetX,
        int targetY,
        GridMetrics gridMetrics,
        Map<Pair<Integer, Integer>, Boolean> spaces,
        Callback callback) {
        final List<WidgetAddAdapter.WidgetProviderWrapper> matchingProviders =
            AppInfoCache.get()
                .getWidgets()
                .stream()
                .filter(appWidgetProviderInfo -> {
                    final int minHeight = gridMetrics.getMinRowCountForWidget(
                        appWidgetProviderInfo);
                    final int minWidth = gridMetrics.getMinColumnCountForWidget(
                        appWidgetProviderInfo);
                    return spaces.containsKey(new Pair<>(minWidth, minHeight));
                })
                .map(awpi ->
                         new WidgetAddAdapter.WidgetProviderWrapper(context, awpi))
                .sorted((o1, o2) -> {
                    final int firstField = o1.appName.compareToIgnoreCase(o2.appName);
                    if (firstField != 0) {
                        return firstField;
                    }
                    return o1.title.compareToIgnoreCase(o2.title);
                })
                .collect(Collectors.toList());
        final RecyclerView recyclerView = new RecyclerView(context);
        final WidgetAddAdapter widgetAddAdapter =
            new WidgetAddAdapter(
                matchingProviders,
                context);
        final String handle = new BottomSheetHelper()
            .setContentView(recyclerView)
            .setIsFixedHeight()
            .show(context, context.getString(R.string.add_widget));
        widgetAddAdapter.setOnClickListener(awpi -> {
            DecorViewManager.get(context).removeView(handle);
            callback.onAddWidget(targetX, targetY, awpi);
        });
        recyclerView.setAdapter(widgetAddAdapter);
        recyclerView.setLayoutManager(
            new GridLayoutManager(context, 2, RecyclerView.VERTICAL, false));
    }

    public interface Callback {
        void onAddWidget(int targetX, int targetY, AppWidgetProviderInfo awpi);
    }
}
