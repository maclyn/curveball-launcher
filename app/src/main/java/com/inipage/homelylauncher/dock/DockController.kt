package com.inipage.homelylauncher.dock

import android.content.Context
import android.os.Handler
import androidx.recyclerview.widget.RecyclerView
import com.inipage.homelylauncher.dock.items.ContextualAppFetcher
import com.inipage.homelylauncher.dock.DockControllerItem
import com.inipage.homelylauncher.model.DockItem
import android.os.Looper
import android.view.View
import com.inipage.homelylauncher.persistence.DatabaseEditor
import com.inipage.homelylauncher.dock.items.AlarmMappedDockItem
import com.inipage.homelylauncher.dock.items.CalendarMappedDockItem
import com.inipage.homelylauncher.dock.items.WeatherDockItem
import com.inipage.homelylauncher.dock.items.PhoneMappedDockItem
import com.inipage.homelylauncher.dock.items.PowerMappedDockItem
import androidx.recyclerview.widget.LinearLayoutManager
import com.inipage.homelylauncher.dock.DockAdapter
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Function
import java.util.stream.Collectors

/**
 * Manages dock items at the bottom of the home screen.
 */
class DockController(val container: RecyclerView) {

    private val foregroundHandler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newFixedThreadPool(1)
    private val appFetcher = ContextualAppFetcher()
    private val adapter = DockAdapter(container.context)

    private var activeDockItems: MutableList<DockControllerItem> = ArrayList()
    private var appBackedItemsCache: MutableMap<Int, DockItem> = HashMap()
    
    fun loadDock() {
        destroyDockImpl()
        backgroundExecutor.submit {
            loadDockItemsImpl()
            foregroundHandler.post { attachDockItemsToView() }
        }
    }

    fun destroyDock() {
        destroyDockImpl()
    }

    private fun loadDockItemsImpl() {
        // Setup the dock controller supporting fields
        appFetcher.reloadPrefs()
        appBackedItemsCache = DatabaseEditor.get().dockPreferences
            .parallelStream()
            .filter { dockItem: DockItem -> dockItem.whenToShow != DockItem.DOCK_SHOW_NEVER }
            .collect(
                Collectors.toConcurrentMap(
                    { obj: DockItem -> obj.whenToShow },
                    Function.identity()
                )
            )

        // Add all the dock items
        activeDockItems.add(AlarmMappedDockItem())
        activeDockItems.add(CalendarMappedDockItem())
        activeDockItems.add(WeatherDockItem())
        activeDockItems.add(PhoneMappedDockItem(appBackedItemsCache))
        activeDockItems.add(PowerMappedDockItem(appBackedItemsCache))
        activeDockItems.addAll(appFetcher.getRecentApps(container.context))
        activeDockItems = activeDockItems
            .parallelStream()
            .sorted { left: DockControllerItem, right: DockControllerItem ->
                if (left.basePriority != right.basePriority) {
                    return@sorted (right.basePriority - left.basePriority).toInt()
                }
                (right.subPriority - left.subPriority).toInt()
            }
            .collect(Collectors.toList())
    }

    private fun destroyDockImpl() {
        container.adapter = null
        adapter.reset()
        appBackedItemsCache.clear()
        activeDockItems.clear()
        for (item in activeDockItems) {
            item.detach()
        }
    }

    private fun attachDockItemsToView() {
        val context = container.context
        container.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        container.adapter = adapter
        activeDockItems.forEachIndexed { index, item ->
            item.attach(object : DockControllerItem.Host {

                override fun getContext() = container.context

                override fun showHostedItem() =
                    adapter.itemLoaded(findLoadedIndex(index), item)

                override fun hideHostedItem() =
                    adapter.itemHidden(findLoadedIndex(index))

                override fun tintLoaded(color: Int) {
                    foregroundHandler.post { adapter.tintLoaded(findLoadedIndex(index)) }
                }
            })
        }
        container.translationY = container.height.toFloat()
        container.alpha = 0f
        container.animate()
            .translationY(0f)
            .alpha(1f)
            .withStartAction { container.visibility = View.VISIBLE }
            .start()
    }

    private fun findLoadedIndex(index: Int) =
        activeDockItems.subList(0, index).count { it.isLoaded }
}