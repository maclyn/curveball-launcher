package com.inipage.homelylauncher.widgets

import android.appwidget.AppWidgetProviderInfo

/**
 * Must be implemented by any surface hosting widgets.
 */
interface WidgetHost {

    enum class Source {
        GridPageController,
        FolderController
    }

    class SourceData(
        val source: Source,
        val pageId: String?,
        val folderId: Int?
    )

    fun requestBindWidget(
        appWidgetId: Int,
        awpi: AppWidgetProviderInfo,
        sourceData: SourceData)

    fun requestConfigureWidget(
        appWidgetId: Int,
        awpi: AppWidgetProviderInfo,
        sourceData: SourceData)
}