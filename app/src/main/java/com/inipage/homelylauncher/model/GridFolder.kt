package com.inipage.homelylauncher.model

import android.content.ContentValues
import android.graphics.ColorSpace.Model
import com.inipage.homelylauncher.persistence.DatabaseHelper

data class GridFolder(
    val id: Int,
    val gridItemId: String,
    var widgetId: Int,
    var width: Int,
    var height: Int
) {

    constructor(id: Int, gridItemId: String) :
            this(
                ModelUtils.unsetValue,
                gridItemId,
                ModelUtils.unsetValue,
                ModelUtils.unsetValue,
                ModelUtils.unsetValue)

    constructor(gridItemId: String) :
            this(ModelUtils.unsetValue, gridItemId)

    var apps: MutableList<GridFolderApp> = ArrayList()
        private set

    fun addApp(app: GridFolderApp) {
        apps.add(app)
    }

    fun setApps(newApps: MutableList<GridFolderApp>) {
        apps = newApps
    }

    fun serialize(): ContentValues {
        val cv = ContentValues()
        cv.put(DatabaseHelper.COLUMN_ID, id)
        cv.put(DatabaseHelper.COLUMN_GRID_ITEM_ID, gridItemId)
        cv.put(DatabaseHelper.COLUMN_WIDGET_ID, widgetId)
        cv.put(DatabaseHelper.COLUMN_WIDTH, width)
        cv.put(DatabaseHelper.COLUMN_HEIGHT, height)
        return cv
    }
}
