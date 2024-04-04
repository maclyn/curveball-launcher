package com.inipage.homelylauncher.dock

import android.content.Context
import android.graphics.Rect
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
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.dock.DockAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    private var adapter: DockAdapter? = null
    private var activeDockItems: MutableList<DockControllerItem> = ArrayList()
    private var appBackedItemsCache: MutableMap<Int, DockItem> = HashMap()
    
    fun loadDock() {
        destroyDockImpl()
        backgroundExecutor.submit {
            loadDockItemsImpl()
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
            .stream()
            .sorted { left, right -> (right.basePriority - left.basePriority).toInt() }
            .collect(Collectors.toList())

        foregroundHandler.post { attachDockItemsToView() }
    }

    private fun destroyDockImpl() {
        container.adapter = null
        for (item in activeDockItems) {
            item.detach()
        }
        activeDockItems.clear()
        appBackedItemsCache.clear()
    }

    private fun attachDockItemsToView() {
        container.adapter = DockAdapter(container.context, activeDockItems)
        activeDockItems.forEachIndexed { index, item ->
            item.attach(object : DockControllerItem.Host {

                override fun getContext() = container.context

                override fun showHostedItem() = adapter?.notifyItemChanged(index) ?: Unit

                override fun hideHostedItem() = adapter?.notifyItemChanged(index) ?: Unit

                override fun tintLoaded(color: Int) = adapter?.notifyItemChanged(index) ?: Unit
            })
        }
        container.alpha = 0f
        container.animate()
            .alpha(1f)
            .withStartAction { container.visibility = View.VISIBLE }
            .setDuration(250L)
            .start()
    }

    init {
        val context = container.context
        val betweenItemSpacePx = context.resources.getDimensionPixelSize(R.dimen.contextual_dock_internal_padding)
        container.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        container.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val idx = container.getChildAdapterPosition(view)
                if (idx == RecyclerView.NO_POSITION) {
                    return
                }
                val isFirstItem = idx == 0
                val isLastItem = idx == ((adapter?.itemCount ?: 0) - 1)
                val isHidden = !(activeDockItems.getOrNull(idx)?.isLoaded ?: false)
                outRect.set(
                    if (isFirstItem) betweenItemSpacePx else 0,
                    0,
                    if (isLastItem || isHidden) 0 else betweenItemSpacePx,
                    0)
            }
        })
    }
}