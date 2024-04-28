package com.inipage.homelylauncher.pocket

import android.content.Context
import android.util.Pair
import android.view.View
import com.google.common.collect.ImmutableList
import com.inipage.homelylauncher.model.ApplicationIcon
import com.inipage.homelylauncher.model.SwipeApp
import com.inipage.homelylauncher.model.SwipeFolder
import com.inipage.homelylauncher.utils.Constants
import com.inipage.homelylauncher.utils.ViewUtils

/**
 * Renders a pocket of folders at the bottom of the homescreen. This pocket animates in with an
 * expand animation that's handled by [how?]. It also connects to
 */
class PocketController(
    private val context: Context,
    private val host: Host,
    private val dropView: PocketControllerDropView,
) : PocketControllerDropView.Host {

    interface Host {
        fun clearActiveDragTarget()
    }

    private var folders: MutableList<SwipeFolder> = ArrayList()

    fun editFolderOrder() {
        ReorderFolderBottomSheet.show(
            context,
            folders
        ) { reorderedFolders: MutableList<SwipeFolder> ->
            folders = reorderedFolders
            // DatabaseEditor.get().saveGestureFavorites(folders)
            // TODO: rebind
        }
    }

    override fun onDragStarted() {
        dropView.alpha = 0f
        dropView.visibility = View.VISIBLE
        dropView
            .animate()
            .alpha(1f)
            .setListener(ViewUtils.onEndListener {

                dropView.alpha = 1f
            })
            .start()
    }

    override fun onDragEnded() {
        dropView
            .animate()
            .alpha(0f)
            .setListener(ViewUtils.onEndListener {
                dropView.visibility = View.GONE
            })
            .start()
    }

    override fun onAppAddedToFolder(folderIdx: Int, app: ApplicationIcon) {
        folders[folderIdx].addApp(Pair(app.packageName, app.activityName))
        // DatabaseEditor.get().saveGestureFavorites(folders)
        host.clearActiveDragTarget()
        // TODO: rebind
    }

    override fun onNewFolderRequested(ai: ApplicationIcon) {
        val newFolder = SwipeFolder(
            "",
            Constants.PACKAGE,
            Constants.DEFAULT_FOLDER_ICON,
            ImmutableList.of(
                SwipeApp(ai.packageName, ai.activityName)
            )
        )
        FolderEditingBottomSheet.show(
            context,
            newFolder,
            true,
            object : FolderEditingBottomSheet.Callback {
                override fun onFolderSaved(
                    title: String,
                    iconPackage: String,
                    iconDrawable: String,
                    reorderedApps: List<SwipeApp>
                ) {
                    newFolder.replaceApps(reorderedApps)
                    newFolder.title = title
                    newFolder.setDrawable(iconDrawable, iconPackage)
                    folders.add(newFolder)
                    // DatabaseEditor.get().saveGestureFavorites(folders)
                    // TODO: rebind
                }

                override fun onFolderDeleted() {}
            })
    }

    override fun getFolders(): List<SwipeFolder> {
        return folders
    }

    private fun editFolder(row: Int) {
        FolderEditingBottomSheet.show(
            context,
            folders[row],
            false,
            object : FolderEditingBottomSheet.Callback {
                override fun onFolderSaved(
                    title: String,
                    iconPackage: String,
                    iconDrawable: String,
                    reorderedApps: List<SwipeApp>
                ) {
                    folders[row].title = title
                    folders[row].setDrawable(iconDrawable, iconPackage)
                    folders[row].replaceApps(reorderedApps)
                    // DatabaseEditor.get().saveGestureFavorites(folders)
                    // TODO: rebind
                }

                override fun onFolderDeleted() {
                    folders.removeAt(row)
                    // DatabaseEditor.get().saveGestureFavorites(folders)
                    // TODO: rebind
                }
            })
    }
}