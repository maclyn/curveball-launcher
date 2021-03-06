package com.inipage.homelylauncher.dock;

import android.content.Context;
import android.util.Pair;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.views.BottomSheetHelper;
import com.inipage.homelylauncher.views.DecorViewManager;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ActivityPickerBottomSheet {

    private String mDecorViewKey;

    public ActivityPickerBottomSheet(Context context, Callback callback, String phrase) {
        final Map<Pair<String, String>, Boolean> hiddenAppsMap =
            DatabaseEditor.get().getHiddenAppsAsMap(true);
        final Stream<ApplicationIconHideable> visibleAppsStream =
            AppInfoCache.get().getAllActivities()
                .parallelStream()
                .filter(applicationIconHideable -> !hiddenAppsMap.containsKey(new Pair<>(
                    applicationIconHideable.getPackageName(),
                    applicationIconHideable.getActivityName())));
        final List<ApplicationIconHideable> allApps =
            visibleAppsStream
                .sorted((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()))
                .collect(Collectors.toList());
        final ActivityPickerAdapter adapter =
            new ActivityPickerAdapter(allApps, (packageName, activityName) -> {
                callback.onActivityPicked(packageName, activityName);
                DecorViewManager.get(context).removeView(mDecorViewKey);
            });
        final RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        mDecorViewKey = new BottomSheetHelper()
            .setContentView(recyclerView)
            .setIsFixedHeight()
            .show(context, context.getString(R.string.choose_right_app, phrase));
    }

    interface Callback {
        void onActivityPicked(String packageName, String activityName);
    }
}
