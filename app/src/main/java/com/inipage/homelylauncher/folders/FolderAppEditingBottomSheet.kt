package com.inipage.homelylauncher.folders

import android.content.Context
import android.util.Pair
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.caches.AppInfoCache
import com.inipage.homelylauncher.model.GridFolder
import com.inipage.homelylauncher.model.GridFolderApp
import com.inipage.homelylauncher.persistence.DatabaseEditor
import com.inipage.homelylauncher.views.BottomSheetHelper

class FolderAppEditingBottomSheet(
    context: Context,
    folder: GridFolder,
    isNewFolder: Boolean,
    callback: Callback
) {

    interface Callback {
        fun onFolderUpdated(newApps: MutableList<GridFolderApp>)

        fun onChangesDismissed()
    }

    private val checkableApps = AppInfoCache.get().checkableActivities
    private val checkableAppsMap =
        checkableApps.associateBy { Pair.create(it.packageName, it.activityName) }
    private val existingApps = folder.apps
    private val existingAppsMap =
        existingApps.associateBy { Pair.create(it.packageName, it.activityName) }
    private val decorViewKey: String

    init {
        existingApps.forEach {
            val k = Pair.create(it.packageName, it.activityName)
            checkableAppsMap[k]?.isChecked = true
        }

        val recyclerView = RecyclerView(context)
        val bottomSheetTitle =
            if (isNewFolder) context.getString(R.string.new_folder_bottom_sheet_title)
            else context.getString(R.string.edit_folder_bottom_sheet_title)
        recyclerView.adapter = ActivitySelectorAdapter(checkableApps)
        recyclerView.layoutManager = LinearLayoutManager(context)
        decorViewKey = BottomSheetHelper()
            .setContentView(recyclerView)
            .setIsFixedHeight()
            .setOnDismissedCallback { bySwipeOrTap ->
                if (bySwipeOrTap) {
                    callback.onChangesDismissed()
                }
            }
            .addActionItem(R.string.save) {
                val newAppsListUnsorted = checkableApps.filter { it.isChecked }
                val previousAppsStillPresent = newAppsListUnsorted.filter {
                    existingAppsMap.contains(Pair.create(it.packageName, it.activityName))
                }
                val newAppsInFolder = newAppsListUnsorted.filter {
                    !existingAppsMap.contains(Pair.create(it.packageName, it.activityName))
                }

                // Copy the old apps in order, and then add
                val newApps = previousAppsStillPresent.sortedBy {
                    existingAppsMap[Pair.create(it.packageName, it.activityName)]?.index ?: 0
                }.toMutableList()
                newApps += newAppsInFolder

                val newList = newApps.mapIndexed { idx, app ->
                    GridFolderApp(folder.id, idx, app.packageName, app.activityName)
                }.toMutableList()
                callback.onFolderUpdated(newList)
            }.show(context, bottomSheetTitle)
    }
}