package com.inipage.homelylauncher.grid

import com.inipage.homelylauncher.model.ClassicGridItem
import com.inipage.homelylauncher.model.ClassicGridPage
import com.inipage.homelylauncher.model.GridItem
import com.inipage.homelylauncher.model.GridPage
import com.inipage.homelylauncher.persistence.DatabaseEditor


class ClassicGridPageController(
    private val host: Host,
    private val page: ClassicGridPage,
    private val startInEditing: Boolean) :
        BaseGridPageController(host, page as GridPage<GridItem>, startInEditing)
{
    override fun commitPage() = DatabaseEditor.get().updatePage(page)

    override fun isItemOnPage(item: GridItem): Boolean =
        (item as? ClassicGridItem)?.pageId == page.id

    override fun getPageId(): String? = page.id

    override fun buildWidgetItem(x: Int, y: Int, width: Int, height: Int, widgetId: Int): GridItem =
        ClassicGridItem.getNewWidgetItem(pageId, x, y, width, height, widgetId)

    override fun updateItemToPage(item: GridItem) {
        (item as? ClassicGridItem)?.updatePageId(pageId)
    }
}