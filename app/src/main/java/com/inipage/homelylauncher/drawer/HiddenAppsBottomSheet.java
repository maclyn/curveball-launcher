package com.inipage.homelylauncher.drawer;

import android.content.Context;
import android.util.Pair;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.views.BottomSheetHelper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HiddenAppsBottomSheet {

    public static void show(Context context, Callback callback) {
        final Map<Pair<String, String>, Boolean> hiddenAppsMap =
            DatabaseEditor.get().getHiddenAppsAsMap();
        final Stream<ApplicationIconHideable> hiddenAppsStream = hiddenAppsMap
            .entrySet()
            .parallelStream()
            .map(entry ->
                     new ApplicationIconHideable(
                         context, entry.getKey().first, entry.getKey().second, entry.getValue()));
        final Stream<ApplicationIconHideable> visibleAppsStream =
            AppInfoCache.get().getAllActivities()
                .parallelStream()
                .filter(applicationIconHideable -> !hiddenAppsMap.containsKey(new Pair<>(
                    applicationIconHideable.getPackageName(),
                    applicationIconHideable.getActivityName())));
        final List<ApplicationIconHideable> hiddenApps =
            Stream.concat(hiddenAppsStream, visibleAppsStream)
                .sorted((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()))
                .collect(Collectors.toList());
        final HiddenAppsAdapter adapter =
            new HiddenAppsAdapter(hiddenApps, callback::onHiddenAppsUpdated);
        final RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        new BottomSheetHelper()
            .setContentView(recyclerView)
            .setFixedScreenPercent(0.75F)
            .show(context, context.getString(R.string.hidden_apps_bottom_sheet_title));
    }

    interface Callback {
        void onHiddenAppsUpdated(List<ApplicationIconHideable> apps);
    }
}
