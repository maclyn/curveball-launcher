package com.inipage.homelylauncher.model

import android.content.ContentValues
import android.graphics.ColorSpace.Model
import com.inipage.homelylauncher.model.ModelUtils.isValueSet
import com.inipage.homelylauncher.persistence.DatabaseHelper

data class GridFolder(
    val id: Int,
    val gridItemId: String,
    var widgetId: Int,
    var width: Int,
    var height: Int
) {

    constructor(newId: Int, newGridItemId: String) :
            this(
                newId,
                newGridItemId,
                ModelUtils.unsetValue,
                ModelUtils.unsetValue,
                ModelUtils.unsetValue)

    constructor(newGridItemId: String) : this(ModelUtils.unsetValue, newGridItemId)

    var apps: MutableList<GridFolderApp> = ArrayList()
        private set

    fun addApp(app: GridFolderApp) {
        apps.add(app)
    }

    fun setApps(newApps: MutableList<GridFolderApp>) {
        apps = newApps
    }

    fun setWidgetDimensions(widgetWidth: Int, widgetHeight: Int) {
        width = widgetWidth
        height = widgetHeight
    }

    fun serialize(): ContentValues {
        val cv = ContentValues()
        if (id.isValueSet()) {
            cv.put(DatabaseHelper.COLUMN_ID, id)
        }
        cv.put(DatabaseHelper.COLUMN_GRID_ITEM_ID, gridItemId)
        cv.put(DatabaseHelper.COLUMN_WIDGET_ID, widgetId)
        cv.put(DatabaseHelper.COLUMN_WIDTH, width)
        cv.put(DatabaseHelper.COLUMN_HEIGHT, height)
        return cv
    }
}
