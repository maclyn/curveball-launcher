package com.inipage.homelylauncher.dock

import android.content.Context
import android.util.Pair
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.caches.AppInfoCache
import com.inipage.homelylauncher.drawer.HiddenAppsAdapter
import com.inipage.homelylauncher.model.ApplicationIconHideable
import com.inipage.homelylauncher.model.DockItem
import com.inipage.homelylauncher.persistence.DatabaseEditor
import com.inipage.homelylauncher.views.BottomSheetHelper

/**
 * Helper bottom sheet for managing apps hidden from the dock.
 */
object HiddenRecentAppsBottomSheet {
    fun showHiddenRecentAppsBottomSheet(context: Context) {
        val hiddenItemMap =
            DatabaseEditor.get().dockPreferences
                .filter { it.whenToShow == DockItem.DOCK_SHOW_NEVER }
                // Don't create hidden items so the map lookup with app list items will succeed
                .map { it.buildAppItem(context, false) }
                .associateWith { true }
        val hiddenAppsMap = DatabaseEditor.get().getHiddenAppsAsMap(true)
        val appList = AppInfoCache.get().allActivities
            .filter { !hiddenAppsMap.containsKey(Pair(it.packageName, it.activityName)) }
            .map {
                ApplicationIconHideable(
                    context,
                    it.packageName,
                    it.activityName,
                    hiddenItemMap.containsKey(it)
                )
            }

        val adapter = HiddenAppsAdapter(appList) { apps: List<ApplicationIconHideable?>? ->
            if (apps == null) return@HiddenAppsAdapter
            DatabaseEditor.get().overwriteHiddenAppDockPreferences(
                apps
                    .filter { it != null && it.isHidden }
                    .map { DockItem.createHiddenItem(it?.packageName, it?.activityName) })
        }
        val recyclerView = RecyclerView(context)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        BottomSheetHelper()
            .setContentView(recyclerView)
            .setIsFixedHeight()
            .show(context, context.getString(R.string.hidden_dock_item_bottom_sheet_title))
    }
}