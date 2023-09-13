package com.inipage.homelylauncher.grid

import com.inipage.homelylauncher.model.ClassicGridItem
import com.inipage.homelylauncher.model.ClassicGridPage
import com.inipage.homelylauncher.model.GridItem
import com.inipage.homelylauncher.model.GridPage
import com.inipage.homelylauncher.model.VerticalGridPage
import com.inipage.homelylauncher.persistence.DatabaseEditor

class VerticalGridPageController(
    private val host: Host,
    private val page: VerticalGridPage) :
    BaseGridPageController(host, page as GridPage<GridItem>, false)
{
    override fun commitPage() = DatabaseEditor.get().updateVerticalPage(page)

    override fun isItemOnPage(item: GridItem): Boolean = true

    override fun getPageId(): String? = null

    override fun buildWidgetItem(x: Int, y: Int, width: Int, height: Int, widgetId: Int): GridItem =
        GridItem.getNewWidgetItem(x, y, width, height, widgetId)

    override fun updateItemToPage(item: GridItem) = Unit
}