package com.inipage.homelylauncher.folders

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
import com.inipage.homelylauncher.model.GridFolder
import com.inipage.homelylauncher.model.GridFolderApp
import com.inipage.homelylauncher.model.GridItem
import com.inipage.homelylauncher.persistence.DatabaseEditor
import com.inipage.homelylauncher.utils.ViewUtils
import com.inipage.homelylauncher.utils.ViewUtils.getRawYForViewAndId
import com.inipage.homelylauncher.views.DraggableLayout
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

    inner class SelectedFolderTarget(
        val folderTarget: AppViewHolder,
        val targetYTranslation: Int,
        private val sourceView: View,
        private val startRawY: Float
    ) {

        var percentComplete: Float = 0.0F

        val currentTranslationAmount: Float
            get() {
                return percentComplete * targetYTranslation
            }

        val newFolderRequest: Boolean
            get() {
                return folderTarget.item.gridFolder == null
            }

        val velocityTracker: VelocityTracker = VelocityTracker.obtain()

        fun addAction(event: MotionEvent, firstPointerId: Int) {
            velocityTracker.addMovement(event)

            val rawY = event.getRawYForViewAndId(sourceView, firstPointerId)
            val deltaY = rawY - startRawY
            percentComplete = if (deltaY > 0) 0.0F else min(-(deltaY / targetYTranslation), 1.0F)
        }
    }

    interface Host {

        fun onStartFolderOpen()

        fun onFolderPartiallyOpen(percent: Float, translationAmount: Float)

        fun onFolderCompletelyOpen()

        fun onFolderClosed()

        // TODO: Getter for all GridPages so we can pre-load all widgets
    }

    private val newFolderSuggestionContainer: View =
        ViewCompat.requireViewById(rootView, R.id.new_folder_suggestion)
    private val openFolderContainer: View =
        ViewCompat.requireViewById(rootView, R.id.open_folder_container)
    private val appsRecyclerView: RecyclerView =
        ViewCompat.requireViewById(rootView, R.id.folder_apps_rv)

    private var selectedFolder: SelectedFolderTarget? = null

    private val gridItem: GridItem?
        get() {
            return selectedFolder?.folderTarget?.item
        }

    private val gridFolder: GridFolder?
        get() {
            return gridItem?.gridFolder
        }

    fun onStartOpenAction(
        appViewHolder: AppViewHolder,
        motionEvent: MotionEvent,
        sourceView: View,
        firstPointerId: Int,
        startRawY: Float
    ) {
        host.onStartFolderOpen()
        selectedFolder = SelectedFolderTarget(
            appViewHolder, rootView.measuredHeight, sourceView, startRawY)
        bindFolderView()
        onOpenMotionEvent(motionEvent, ACTION_MOVE, firstPointerId, startRawY)
    }

    fun onOpenMotionEvent(
        event: MotionEvent,
        action: Int,
        firstPointerId: Int,
        startRawY: Float
    ) {
        val target = selectedFolder ?: return
        target.addAction(event, firstPointerId)
        when (action) {
            ACTION_MOVE -> {
                rootView.translationY = target.targetYTranslation - target.currentTranslationAmount
                host.onFolderPartiallyOpen(target.percentComplete, target.currentTranslationAmount)
            }
            ACTION_UP -> {
                if (target.percentComplete >= 1.0) {
                    onAnimationInComplete()
                    host.onFolderCompletelyOpen()
                    return
                }
                val folder = selectedFolder ?: return
                rootView.runAnimationFromBrainSlug(folder.velocityTracker, firstPointerId)
            }
            ACTION_CANCEL -> {
                onAnimationOutComplete()
            }
        }
    }

    fun isFolderOpen(): Boolean {
        return selectedFolder != null
    }

    fun closeFolder() {
        rootView.triggerExitAnimation()
    }

    private fun bindFolderView() {
        val newFolderRequest = gridFolder == null
        rootView.visibility = VISIBLE
        newFolderSuggestionContainer.visibility = if (newFolderRequest) VISIBLE else GONE
        openFolderContainer.visibility  = if (newFolderRequest) GONE else VISIBLE

        if (!newFolderRequest) {
            appsRecyclerView.adapter = FolderAppRecyclerViewAdapter(gridFolder?.apps ?: listOf())
            // TODO: Show desired widget
        }

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
                    bindFolderView()
                }

                override fun onChangesDismissed() {
                    val gridFolder = gridFolder ?: return
                    DatabaseEditor.get().deleteGridFolder(newFolder)
                    selectedFolder?.folderTarget?.item?.updateGridFolder(null)
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
                    bindFolderView()
                }

                override fun onChangesDismissed() = Unit
            })
    }

    private fun onFolderReorderRequested() {
        val folder = gridFolder ?: return
        FolderReorderBottomSheet.show(context, folder.apps) { newApps ->
            folder.setApps(newApps)
            DatabaseEditor.get().updateGridFolder(folder)
            bindFolderView()
        }
    }

    init {
        rootView.attachHost(this)

        // TODO: Load up all possible widgets

        rootView.findViewById<ImageView>(R.id.collapse_folder_button).setOnClickListener {
            closeFolder()
        }
        rootView.findViewById<ImageView>(R.id.show_folder_menu).setOnClickListener {
            val menu = PopupMenu(it.context, it, Gravity.BOTTOM)
            menu.menu.add("Edit items").setOnMenuItemClickListener {
                onFolderEditRequested()
                true
            }
            menu.menu.add("Reorder items").setOnMenuItemClickListener {
                onFolderReorderRequested()
                true
            }
            menu.menu.add("Delete folder").setOnMenuItemClickListener {
                val folder = gridFolder ?: return@setOnMenuItemClickListener false
                DatabaseEditor.get().deleteGridFolder(folder)
                gridItem?.updateGridFolder(null)
                bindFolderView()
                closeFolder()
                true
            }

            // TODO: Widget options

            menu.show()
        }
        appsRecyclerView.layoutManager = GridLayoutManager(context, 5, RecyclerView.VERTICAL, false)
    }

    override fun onAnimationPartial(percentComplete: Float, translationMagnitude: Float) {
        // Percent complete = how close are we to "fully expanded"
        // Translation magnitude = how far have we dragged
        val folder = selectedFolder ?: return
        rootView.translationY = folder.targetYTranslation - (folder.targetYTranslation * percentComplete)
        host.onFolderPartiallyOpen(percentComplete, folder.targetYTranslation - translationMagnitude)
    }

    override fun onAnimationOutComplete() {
        selectedFolder = null
        rootView.visibility = GONE
        host.onFolderPartiallyOpen(0.0f, 0.0f)
        host.onFolderClosed()
    }

    override fun onAnimationInComplete() {
        host.onFolderPartiallyOpen(
            1.0F,
            selectedFolder?.targetYTranslation?.toFloat() ?: 0F)
        rootView.translationY = 0.0F
        if (selectedFolder?.newFolderRequest == true) {
            onNewFolderRequested()
        }
    }
}