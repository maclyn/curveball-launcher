package com.inipage.homelylauncher.folders

import android.app.ActionBar.LayoutParams
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import android.view.VelocityTracker
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.grid.AppViewHolder
import com.inipage.homelylauncher.model.ClassicGridPage
import com.inipage.homelylauncher.model.GridFolder
import com.inipage.homelylauncher.model.GridFolderApp
import com.inipage.homelylauncher.model.GridItem
import com.inipage.homelylauncher.persistence.DatabaseEditor
import com.inipage.homelylauncher.utils.ViewUtils
import com.inipage.homelylauncher.utils.ViewUtils.getRawYForViewAndId
import com.inipage.homelylauncher.views.DraggableLayout
import com.inipage.homelylauncher.views.ProvidesOverallDimensions
import com.inipage.homelylauncher.widgets.WidgetAddBottomSheet
import com.inipage.homelylauncher.widgets.WidgetHost
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
        val targetYTranslation: Int,
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

        fun onFolderCompletelyOpen()

        fun onFolderClosed()

        fun getGridPages(): List<ClassicGridPage>
    }

    private val newFolderSuggestionContainer: View =
        ViewCompat.requireViewById(rootView, R.id.new_folder_suggestion)
    private val openFolderContainer: View =
        ViewCompat.requireViewById(rootView, R.id.open_folder_container)
    private val appsRecyclerView: RecyclerView =
        ViewCompat.requireViewById(rootView, R.id.folder_apps_rv)

    private var folderTarget: AppViewHolder? = null
    private var activeFolderAnimation: FolderAnimationTracker? = null
    private var maxRecyclerViewHeight: Int = 0

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

        onOpenMotionEvent(motionEvent, ACTION_MOVE, firstPointerId, startRawY)
    }

    fun onOpenMotionEvent(
        event: MotionEvent,
        action: Int,
        firstPointerId: Int,
        startRawY: Float
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
                    host.onFolderCompletelyOpen()
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
        host.onFolderPartiallyOpen(
            1.0F,
            activeFolderAnimation?.targetYTranslation?.toFloat() ?: 0F)
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

    private fun sizeRecyclerView() {
        if (!newFolderRequest) {

            // TODO: Show desired widget
        } else {

        }

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
        menu.menu.add(context.getString(R.string.add_widget)).setOnMenuItemClickListener {
            ViewUtils.performSyntheticMeasure(rootView)
            WidgetAddBottomSheet.show(context, rootView.measuredWidth, 1000, {
                // todo: lmao
            })
            true
        }

        // TODO: Widget options

        menu.show()
    }

    init {
        rootView.attachHost(this)

        // TODO: Load up all possible widgets, and bind 'em

        rootView.findViewById<ImageView>(R.id.collapse_folder_button).setOnClickListener {
            closeFolder()
        }
        rootView.findViewById<ImageView>(R.id.show_folder_menu).setOnClickListener {
            showMenu(it)
        }
        appsRecyclerView.layoutManager = GridLayoutManager(context, 5, RecyclerView.VERTICAL, false)
    }
}