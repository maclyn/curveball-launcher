package com.inipage.homelylauncher.model

import android.content.ContentValues
import com.inipage.homelylauncher.persistence.DatabaseHelper

data class GridFolderApp(
    val id: Int,
    val gridFolderId: Int,
    var index: Int,
    val packageName: String,
    val activityName: String
) {

    fun serialize(): ContentValues {
        val cv = ContentValues()
        cv.put(DatabaseHelper.COLUMN_ID, id)
        cv.put(DatabaseHelper.COLUMN_GRID_FOLDER_ID, gridFolderId)
        cv.put(DatabaseHelper.COLUMN_INDEX, index)
        cv.put(DatabaseHelper.COLUMN_DATA_STRING_1, packageName)
        cv.put(DatabaseHelper.COLUMN_DATA_STRING_2, activityName)
        return cv
    }
}
