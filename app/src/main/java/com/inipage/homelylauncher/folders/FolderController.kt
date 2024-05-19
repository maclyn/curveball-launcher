package com.inipage.homelylauncher.folders

import android.app.ActionBar.LayoutParams
import android.app.AlertDialog
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import android.view.VelocityTracker
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.grid.AppViewHolder
import com.inipage.homelylauncher.model.ClassicGridPage
import com.inipage.homelylauncher.model.GridFolder
import com.inipage.homelylauncher.model.GridFolderApp
import com.inipage.homelylauncher.model.GridItem
import com.inipage.homelylauncher.model.ModelUtils
import com.inipage.homelylauncher.model.ModelUtils.isValueSet
import com.inipage.homelylauncher.persistence.DatabaseEditor
import com.inipage.homelylauncher.utils.ViewUtils
import com.inipage.homelylauncher.utils.ViewUtils.getRawYForViewAndId
import com.inipage.homelylauncher.views.DraggableLayout
import com.inipage.homelylauncher.views.ProvidesOverallDimensions
import com.inipage.homelylauncher.widgets.WidgetAddBottomSheet
import com.inipage.homelylauncher.widgets.WidgetHost
import com.inipage.homelylauncher.widgets.WidgetLifecycleUtils
import com.inipage.homelylauncher.widgets.WidgetLifecycleUtils.findIdealSize
import com.inipage.homelylauncher.widgets.WidgetLifecycleUtils.maxLayoutHeight
import com.inipage.homelylauncher.widgets.WidgetLifecycleUtils.maxLayoutWidth
import com.inipage.homelylauncher.widgets.WidgetLifecycleUtils.minLayoutHeight
import com.inipage.homelylauncher.widgets.WidgetLifecycleUtils.minLayoutWidth
import com.inipage.homelylauncher.widgets.WidgetLifecycleUtils.supportsHorizontalResize
import com.inipage.homelylauncher.widgets.WidgetLifecycleUtils.supportsResizing
import com.inipage.homelylauncher.widgets.WidgetLifecycleUtils.supportsVerticalResize
import kotlin.math.min

/**
 * Renders a folder (a set of apps and sometimes a widget tied to a given app grid item) at the
 * bottom of the home screen.
 */
class FolderController(
    private val context: Context,
    private val host: Host,
    val rootView: DraggableLayout
) : DraggableLayout.Host {

    inner class FolderAnimationTracker(
        var targetYTranslation: Int,
        private val sourceView: View,
        private val startRawY: Float
    ) {

        var percentComplete: Float = 0.0F

        val currentTranslationAmount: Float
            get() {
                return percentComplete * targetYTranslation
            }

        val velocityTracker: VelocityTracker = VelocityTracker.obtain()

        fun addAction(event: MotionEvent, firstPointerId: Int) {
            velocityTracker.addMovement(event)

            val rawY = event.getRawYForViewAndId(sourceView, firstPointerId)
            val deltaY = rawY - startRawY
            percentComplete = if (deltaY > 0) 0.0F else min(-(deltaY / targetYTranslation), 1.0F)
        }
    }

    interface Host : WidgetHost {

        fun onStartFolderOpen()

        fun onFolderPartiallyOpen(percent: Float, translationAmount: Float)

        fun onFolderCompletelyOpen(translationAmount: Float)

        fun onFolderClosed()

        fun getGridPages(): List<ClassicGridPage>
    }

    private val newFolderSuggestionContainer: View =
        ViewCompat.requireViewById(rootView, R.id.new_folder_suggestion)
    private val openFolderContainer: View =
        ViewCompat.requireViewById(rootView, R.id.open_folder_container)
    private val folderIconsContainer: View =
        ViewCompat.requireViewById(rootView, R.id.folder_icons_container)
    private val appsRecyclerView: RecyclerView =
        ViewCompat.requireViewById(rootView, R.id.folder_apps_rv)
    private val widgetsContainer: FrameLayout =
        ViewCompat.requireViewById(rootView, R.id.folder_widgets_holder)

    private val folderIdToWidgetView: MutableMap<Int, View> = mutableMapOf()

    private var folderTarget: AppViewHolder? = null
    private var activeFolderAnimation: FolderAnimationTracker? = null
    private var maxRecyclerViewHeight: Int = 0
    private var maxWidgetHeight: Int = 0

    val newFolderRequest: Boolean
        get() {
            return folderTarget?.item?.gridFolder == null
        }

    private val gridItem: GridItem?
        get() {
            return folderTarget?.item
        }

    private val gridFolder: GridFolder?
        get() {
            return gridItem?.gridFolder
        }

    fun isFolderOpen(): Boolean = folderTarget != null

    fun onStartOpenAction(
        appViewHolder: AppViewHolder,
        motionEvent: MotionEvent,
        sourceView: View,
        firstPointerId: Int,
        startRawY: Float
    ) {
        host.onStartFolderOpen()

        folderTarget = appViewHolder
        bindFolderView(gridFolder)
        activeFolderAnimation =
            FolderAnimationTracker(rootView.measuredHeight, sourceView, startRawY)

        onOpenMotionEvent(motionEvent, ACTION_MOVE, firstPointerId)
    }

    fun onOpenMotionEvent(
        event: MotionEvent,
        action: Int,
        firstPointerId: Int
    ) {
        val animation = activeFolderAnimation ?: return
        animation.addAction(event, firstPointerId)
        when (action) {
            ACTION_MOVE -> {
                rootView.translationY = animation.targetYTranslation - animation.currentTranslationAmount
                rootView.alpha = animation.percentComplete
                host.onFolderPartiallyOpen(animation.percentComplete, animation.currentTranslationAmount)
            }
            ACTION_UP -> {
                if (animation.percentComplete >= 1.0) {
                    onAnimationInComplete()
                    return
                }
                rootView.runAnimationFromBrainSlug(animation.velocityTracker, firstPointerId)
            }
            ACTION_CANCEL -> {
                onAnimationOutComplete()
            }
        }
    }

    fun closeFolder() {
        rootView.triggerExitAnimation()
    }

    fun onHomeActivitySized() {
        val dimensionProvider =
            ViewUtils.requireActivityOf(context) as? ProvidesOverallDimensions ?: return
        val height = dimensionProvider.provideOverallBounds().height()
        maxRecyclerViewHeight = (height * 0.3F).toInt()
        maxWidgetHeight = (height * 0.4F).toInt()
    }

    override fun onAnimationPartial(percentComplete: Float, translationY: Float) {
        // Percent complete = how close are we to "fully expanded"
        // Translation magnitude = how far have we dragged
        val animation = activeFolderAnimation ?: return
        rootView.translationY = animation.targetYTranslation - (animation.targetYTranslation * percentComplete)
        rootView.alpha = percentComplete
        host.onFolderPartiallyOpen(
            percentComplete,
            animation.targetYTranslation - translationY)
    }

    override fun onAnimationOutComplete() {
        folderTarget = null
        activeFolderAnimation = null
        rootView.visibility = GONE
        host.onFolderPartiallyOpen(0.0f, 0.0f)
        host.onFolderClosed()
    }

    override fun onAnimationInComplete() {
        host.onFolderCompletelyOpen(activeFolderAnimation?.targetYTranslation?.toFloat() ?: 0F)
        rootView.translationY = 0.0F
        rootView.alpha = 1.0F
        if (newFolderRequest) {
            onNewFolderRequested()
        }
    }

    private fun bindFolderView(gridFolder: GridFolder?) {
        val newFolderRequest = gridFolder == null
        rootView.visibility = VISIBLE
        newFolderSuggestionContainer.visibility = if (newFolderRequest) VISIBLE else GONE
        openFolderContainer.visibility  = if (newFolderRequest) GONE else VISIBLE
        appsRecyclerView.adapter = FolderAppRecyclerViewAdapter(gridFolder?.apps ?: listOf())
        folderIdToWidgetView.entries.forEach {
            it.value.visibility = GONE
        }
        folderIdToWidgetView.entries.forEach {
            it.value.visibility = GONE
        }
        widgetsContainer.visibility = GONE
        gridFolder?.let {
            folderIdToWidgetView[it.id]?.let {
                it.visibility = VISIBLE
                widgetsContainer.visibility = VISIBLE
            }
        }

        // Since we need to provide the widget with a known max size, we cap the apps view at 30%
        // of screen height or less
        ViewUtils.performSyntheticMeasure(appsRecyclerView)
        ViewUtils.setHeight(
            appsRecyclerView,
            if (appsRecyclerView.measuredHeight > maxRecyclerViewHeight)
                maxRecyclerViewHeight
            else LayoutParams.WRAP_CONTENT)

        ViewUtils.performSyntheticMeasure(rootView)
    }

    private fun onNewFolderRequested() {
        val targetFolderId = gridItem?.id ?: return
        val newFolder = DatabaseEditor.get().insertNewGridFolder(targetFolderId)
        gridItem?.updateGridFolder(newFolder)

        FolderAppEditingBottomSheet(
            context,
            newFolder,
            true,
            object : FolderAppEditingBottomSheet.Callback {
                override fun onFolderUpdated(newApps: MutableList<GridFolderApp>) {
                    val gridFolder = gridFolder ?: return
                    gridFolder.setApps(newApps)
                    DatabaseEditor.get().updateGridFolder(gridFolder)
                    bindFolderView(gridFolder)
                }

                override fun onChangesDismissed() {
                    DatabaseEditor.get().deleteGridFolder(newFolder)
                    gridItem?.updateGridFolder(null)
                    closeFolder()
                }
            })
    }

    private fun onFolderEditRequested() {
        val folder = gridFolder ?: return
        FolderAppEditingBottomSheet(
            context,
            folder,
            true,
            object : FolderAppEditingBottomSheet.Callback {
                override fun onFolderUpdated(newApps: MutableList<GridFolderApp>) {
                    folder.setApps(newApps)
                    DatabaseEditor.get().updateGridFolder(folder)
                    bindFolderView(gridFolder)
                    onFolderContentsHeightUpdated()
                }

                override fun onChangesDismissed() = Unit
            })
    }

    private fun onFolderReorderRequested() {
        val folder = gridFolder ?: return
        FolderReorderBottomSheet.show(context, folder.apps) { newApps ->
            folder.setApps(newApps)
            DatabaseEditor.get().updateGridFolder(folder)
            bindFolderView(gridFolder)
        }
    }

    private fun showMenu(view: View) {
        val menu = PopupMenu(view.context, view, Gravity.BOTTOM)
        menu.menu.add(context.getString(R.string.edit_folder_items)).setOnMenuItemClickListener {
            onFolderEditRequested()
            true
        }
        menu.menu.add(context.getString(R.string.reorder_folder_items)).setOnMenuItemClickListener {
            onFolderReorderRequested()
            true
        }
        menu.menu.add(context.getString(R.string.delete_folder)).setOnMenuItemClickListener {
            val folder = gridFolder ?: return@setOnMenuItemClickListener false
            DatabaseEditor.get().deleteGridFolder(folder)
            gridItem?.updateGridFolder(null)
            bindFolderView(gridFolder)
            closeFolder()
            true
        }

        val widgetId = gridFolder?.widgetId
        if (widgetId?.isValueSet() == true) {
            val awpi = widgetId.let { WidgetLifecycleUtils.getAppWidgetProviderInfo(context, it) }
            if (awpi?.supportsResizing() == true) {
                menu.menu.add(context.getString(R.string.resize_widget))
                    .setOnMenuItemClickListener {
                        showWidgetResizeDialog()
                        true
                    }
            }
            menu.menu.add(context.getString(R.string.remove_widget)).setOnMenuItemClickListener {
                val folder = gridFolder ?: return@setOnMenuItemClickListener false
                folderIdToWidgetView[folder.id]?.let {
                    widgetsContainer.removeView(it)
                    folderIdToWidgetView.remove(folder.id)
                }
                folder.widgetId = ModelUtils.unsetValue
                folder.setWidgetDimensions(ModelUtils.unsetValue, ModelUtils.unsetValue)
                DatabaseEditor.get().updateGridFolder(folder)
                onFolderContentsHeightUpdated()
                true
            }
        } else {
            menu.menu.add(context.getString(R.string.add_widget)).setOnMenuItemClickListener {
                ViewUtils.performSyntheticMeasure(rootView)
                WidgetAddBottomSheet.show(
                    context,
                    rootView.measuredWidth,
                    maxWidgetHeight
                ) {
                    startAddWidgetFlow(it)
                }
                true
            }
        }

        menu.show()
    }

    private fun showWidgetResizeDialog() {
        val folder = gridFolder?: return
        val widgetId = folder.widgetId
        val awpi = WidgetLifecycleUtils.getAppWidgetProviderInfo(context, widgetId) ?: return
        val minWidth = awpi.minLayoutWidth()
        val minHeight = awpi.minLayoutHeight()
        val maxWidth = awpi.maxLayoutWidth(folderIconsContainer.width) ?: minWidth
        val maxHeight = awpi.maxLayoutHeight(maxWidgetHeight) ?: minHeight
        val widthSpan = maxWidth - minWidth
        val heightSpan = maxHeight - minHeight
        val currWidth = folder.width
        val currHeight = folder.height
        val startWidthPercent =
            if (maxWidth == minWidth) 0F else (currWidth - minWidth) / (widthSpan).toFloat()
        val startHeightPercent =
            if (maxHeight == minHeight) 0F else (currHeight - minHeight) / (heightSpan).toFloat()

        val layout =
            LayoutInflater.from(context).inflate(R.layout.dialog_resize_widget, rootView, false)
        val widthView = ViewCompat.requireViewById<SeekBar>(layout, R.id.width_seekbar)
        val widthLabel = ViewCompat.requireViewById<TextView>(layout, R.id.width_label)
        val heightView = ViewCompat.requireViewById<SeekBar>(layout, R.id.height_seekbar)
        val heightLabel = ViewCompat.requireViewById<TextView>(layout, R.id.height_label)
        widthView.visibility = if (awpi.supportsHorizontalResize()) VISIBLE else GONE
        heightView.visibility = if (awpi.supportsVerticalResize()) VISIBLE else GONE
        widthLabel.visibility = if (awpi.supportsHorizontalResize()) VISIBLE else GONE
        heightLabel.visibility = if (awpi.supportsVerticalResize()) VISIBLE else GONE
        widthView.progress = (startWidthPercent * 100).toInt()
        heightView.progress = (startHeightPercent * 100).toInt()

        AlertDialog.Builder(context).setView(layout).setPositiveButton(R.string.update_size
        ) { _, _ ->
            val widthPercent = widthView.progress / 100F
            val heightPercent = heightView.progress / 100F
            val newWidth = (minWidth + (widthSpan * widthPercent)).toInt()
            val newHeight = (minHeight + (heightSpan * heightPercent)).toInt()
            folder.setWidgetDimensions(newWidth, newHeight)
            DatabaseEditor.get().updateGridFolder(folder)
            widgetsContainer.removeView(folderIdToWidgetView[folder.id])
            addWidgetToContainer(folder.id,  folder.widgetId, folder.width, folder.height)
            onFolderContentsHeightUpdated()
        }.show()
    }

    private fun startAddWidgetFlow(awpi: AppWidgetProviderInfo) {
        val appWidgetId = WidgetLifecycleUtils.getAppWidgetHost().allocateAppWidgetId()
        WidgetLifecycleUtils.startTransaction(appWidgetId, awpi)
        val didBind = WidgetLifecycleUtils
            .getAppWidgetManager(context)
            .bindAppWidgetIdIfAllowed(appWidgetId, awpi.provider)
        if (!didBind) {
            host.requestBindWidget(
                appWidgetId,
                awpi,
                WidgetHost.SourceData(WidgetHost.Source.FolderController, null, gridFolder?.id ?: 0))
        } else {
            onWidgetBound()
        }
    }

    fun onWidgetBound() {
        val transaction = WidgetLifecycleUtils.activeTransaction ?: return
        if (transaction.apwi.configure == null) {
            onWidgetConfigureComplete()
            return
        }
        host.requestConfigureWidget(
            transaction.appWidgetId,
            transaction.apwi,
            WidgetHost.SourceData(WidgetHost.Source.FolderController, null, gridFolder?.id ?: 0))
    }

    fun onWidgetConfigureComplete() {
        val transaction = WidgetLifecycleUtils.activeTransaction ?: return
        WidgetLifecycleUtils.endTransaction()
        val folder = gridFolder ?: return
        folder.widgetId = transaction.appWidgetId

        // Size the new widget
        val widgetDimens =
            transaction.apwi.findIdealSize(folderIconsContainer.width, maxWidgetHeight)
        val widgetWidth = widgetDimens.first
        val widgetHeight = widgetDimens.second
        folder.setWidgetDimensions(widgetWidth, widgetHeight)

        // Update view + DB
        widgetsContainer.visibility = VISIBLE
        addWidgetToContainer(folder.id, transaction.appWidgetId, folder.width, folder.height)
        DatabaseEditor.get().updateGridFolder(folder)
        onFolderContentsHeightUpdated()
    }

    private fun addWidgetToContainer(gridFolderId: Int, appWidgetId: Int, widthPx: Int, heightPx: Int): View? {
        val view =
            WidgetLifecycleUtils.buildAppWidgetHostView(context, appWidgetId, widthPx, heightPx) ?: return null
        widgetsContainer.addView(
            view,
            FrameLayout.LayoutParams(widthPx, heightPx).also {
                it.gravity = Gravity.CENTER
            })
        folderIdToWidgetView[gridFolderId] = view
        widgetsContainer.visibility = VISIBLE
        return view
    }

    private fun onFolderContentsHeightUpdated() {
        ViewUtils.performSyntheticMeasure(rootView)
        val newHeight = rootView.measuredHeight
        activeFolderAnimation?.targetYTranslation = newHeight
        host.onFolderCompletelyOpen(newHeight.toFloat())
    }

    init {
        rootView.attachHost(this)

        val foldersWithWidgets =
            host.getGridPages()
                .flatMap { it.items }
                .mapNotNull { it.gridFolder }
                .filter { it.widgetId.isValueSet() }
        foldersWithWidgets.forEach {
            val view = addWidgetToContainer(it.id, it.widgetId, it.width, it.height) ?: return@forEach
            folderIdToWidgetView[it.id] = view
            // TODO: This doesn't work if you change the density of your phone
            view.visibility = GONE
        }

        rootView.findViewById<ImageView>(R.id.collapse_folder_button).setOnClickListener {
            closeFolder()
        }
        rootView.findViewById<ImageView>(R.id.show_folder_menu).setOnClickListener {
            showMenu(it)
        }
        appsRecyclerView.layoutManager = GridLayoutManager(context, 5, RecyclerView.VERTICAL, false)
    }
}