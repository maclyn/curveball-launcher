package com.inipage.homelylauncher.persistence;


import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_ACTIVITY_NAME;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_DATA_INT_1;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_DATA_STRING_1;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_DATA_STRING_2;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_GRID_FOLDER_ID;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_GRID_ITEM_ID;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_GRID_ITEM_TYPE;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_HEIGHT;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_ID;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_INDEX;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_ITEM_ID;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_PACKAGE;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_PAGE_ID;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_POSITION_X;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_POSITION_Y;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_WHEN_TO_SHOW;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_WIDGET_ID;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_WIDTH;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLES;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLE_DOCK;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLE_GRID_FOLDER;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLE_GRID_FOLDER_APPS;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLE_GRID_ITEM;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLE_GRID_PAGE;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLE_HIDDEN_APPS;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.inipage.homelylauncher.HomeActivity;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.model.ClassicGridItem;
import com.inipage.homelylauncher.model.ClassicGridPage;
import com.inipage.homelylauncher.model.DockItem;
import com.inipage.homelylauncher.model.GridFolder;
import com.inipage.homelylauncher.model.GridFolderApp;
import com.inipage.homelylauncher.utils.Constants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class DatabaseEditor {

    private static DatabaseEditor s_INSTANCE;

    private final SQLiteDatabase mDB;

    private DatabaseEditor(Context context) {
        mDB = new DatabaseHelper(context).getWritableDatabase();
    }

    public static void seed(Context context) {
        if (s_INSTANCE == null) {
            s_INSTANCE = new DatabaseEditor(context);
        }
    }

    public static DatabaseEditor get() {
        return s_INSTANCE;
    }

    // Grid page table
    public List<ClassicGridPage> getGridPages() {
        final Map<String, ClassicGridPage> pageIdToPage = new HashMap<>();
        Cursor cursor = mDB.rawQuery(
        "SELECT * FROM " + TABLE_GRID_PAGE + " ORDER BY " + COLUMN_INDEX + " desc",null);
        getPages:
        {
            if (!cursor.moveToFirst()) {
                break getPages;
            }

            final int idColumn = cursor.getColumnIndex(COLUMN_PAGE_ID);
            final int indexColumn = cursor.getColumnIndex(COLUMN_INDEX);
            final int widthColumn = cursor.getColumnIndex(COLUMN_WIDTH);
            final int heightColumn = cursor.getColumnIndex(COLUMN_HEIGHT);
            while (!cursor.isAfterLast()) {
                final String id = cursor.getString(idColumn);
                final int index = cursor.getInt(indexColumn);
                final int width = cursor.getInt(widthColumn);
                final int height = cursor.getInt(heightColumn);
                pageIdToPage.put(id, new ClassicGridPage(new ArrayList<>(), id, index, width, height));
                cursor.moveToNext();
            }
        }
        cursor.close();

        // Create a map of grid item ID -> grid folder
        cursor = mDB.rawQuery("SELECT * FROM " + TABLE_GRID_FOLDER, null);
        final Map<String, GridFolder> gridItemIdToFolderMap = new HashMap<>();
        final Map<Integer, GridFolder> gridFolderIdToFolderMap = new HashMap<>();
        getFolders: {
            if (!cursor.moveToFirst()) {
                break getFolders;
            }

            final int idColumn = cursor.getColumnIndex(COLUMN_ID);
            final int gridItemIdColumn = cursor.getColumnIndex(COLUMN_GRID_ITEM_ID);
            final int widgetIdColumn = cursor.getColumnIndex(COLUMN_WIDGET_ID);
            final int widthColumn = cursor.getColumnIndex(COLUMN_WIDTH);
            final int heightColumn = cursor.getColumnIndex(COLUMN_HEIGHT);
            while (!cursor.isAfterLast()) {
                final int id = cursor.getInt(idColumn);
                final String gridItemId = cursor.getString(gridItemIdColumn);
                final int widgetId = cursor.getInt(widgetIdColumn);
                final int width = cursor.getInt(widthColumn);
                final int height = cursor.getInt(heightColumn);
                final GridFolder gridFolder =
                    new GridFolder(id, gridItemId, widgetId, width, height);
                gridItemIdToFolderMap.put(gridItemId, gridFolder);
                gridFolderIdToFolderMap.put(id, gridFolder);
                cursor.moveToNext();
            }
        }
        cursor.close();

        // Fill out apps in grid folders
        cursor = mDB.rawQuery("SELECT * FROM " + TABLE_GRID_FOLDER_APPS, null);
        getFolderApps: {
            if (!cursor.moveToFirst()) {
                break getFolderApps;
            }

            final int idColumn = cursor.getColumnIndex(COLUMN_ID);
            final int gridFolderIdColumn = cursor.getColumnIndex(COLUMN_GRID_FOLDER_ID);
            final int indexColumn = cursor.getColumnIndex(COLUMN_INDEX);
            final int packageNameColumn = cursor.getColumnIndex(COLUMN_DATA_STRING_1);
            final int activityNameColumn = cursor.getColumnIndex(COLUMN_DATA_STRING_2);
            while (!cursor.isAfterLast()) {
                final int id = cursor.getInt(idColumn);
                final int gridFolderId = cursor.getInt(gridFolderIdColumn);
                final int index = cursor.getInt(indexColumn);
                final String packageName = cursor.getString(packageNameColumn);
                final String activityName = cursor.getString(activityNameColumn);
                final GridFolderApp gridFolderApp =
                    new GridFolderApp(id, gridFolderId, index, packageName, activityName);
                if (gridFolderIdToFolderMap.containsKey(gridFolderId)) {
                    Objects.requireNonNull(gridFolderIdToFolderMap.get(gridFolderId))
                        .addApp(gridFolderApp);
                }
                cursor.moveToNext();
            }
        }
        cursor.close();

        cursor = mDB.rawQuery(
            "SELECT * FROM " + TABLE_GRID_ITEM,
            null);
        getItems:
        {
            if (!cursor.moveToFirst()) {
                break getItems;
            }

            final int xColumn = cursor.getColumnIndex(COLUMN_POSITION_X);
            final int yColumn = cursor.getColumnIndex(COLUMN_POSITION_Y);
            final int itemIdColumn = cursor.getColumnIndex(COLUMN_ITEM_ID);
            final int pageIdColumn = cursor.getColumnIndex(COLUMN_PAGE_ID);
            final int widthColumn = cursor.getColumnIndex(COLUMN_WIDTH);
            final int heightColumn = cursor.getColumnIndex(COLUMN_HEIGHT);
            final int typeColumn = cursor.getColumnIndex(COLUMN_GRID_ITEM_TYPE);
            final int dataStringOneColumn =
                cursor.getColumnIndex(COLUMN_DATA_STRING_1);
            final int dataStringTwoColumn =
                cursor.getColumnIndex(COLUMN_DATA_STRING_2);
            final int dataIntColumn =
                cursor.getColumnIndex(COLUMN_DATA_INT_1);
            while (!cursor.isAfterLast()) {
                final String gridItemId = cursor.getString(itemIdColumn);
                final ClassicGridItem gridItem = new ClassicGridItem(
                    gridItemId,
                    cursor.getString(pageIdColumn),
                    cursor.getInt(xColumn),
                    cursor.getInt(yColumn),
                    cursor.getInt(widthColumn),
                    cursor.getInt(heightColumn),
                    cursor.getInt(typeColumn),
                    gridItemIdToFolderMap.getOrDefault(gridItemId, null),
                    cursor.getString(dataStringOneColumn),
                    cursor.getString(dataStringTwoColumn),
                    cursor.getInt(dataIntColumn));
                Objects.requireNonNull(pageIdToPage.get(gridItem.getPageId()))
                    .getItems()
                    .add(gridItem);
                cursor.moveToNext();
            }
        }
        cursor.close();

        return pageIdToPage.entrySet().stream()
            .sorted(Comparator.comparingInt(o -> o.getValue().getIndex()))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    public void saveGridPages(List<ClassicGridPage> gridPages) {
        mDB.beginTransaction();
        mDB.delete(TABLE_GRID_PAGE, null, null);
        mDB.delete(TABLE_GRID_ITEM, null, null);
        for (ClassicGridPage gridPage : gridPages) {
            writePage(gridPage);
        }
        mDB.setTransactionSuccessful();
        mDB.endTransaction();
    }

    private void writePage(ClassicGridPage gridPage) {
        dropPage(gridPage.getID());

        for (ClassicGridItem gridItem : gridPage.getItems()) {
            final ContentValues itemCV = new ContentValues();
            itemCV.put(COLUMN_ITEM_ID, gridItem.getID());
            itemCV.put(COLUMN_PAGE_ID, gridPage.getID());
            itemCV.put(COLUMN_POSITION_X, gridItem.getX());
            itemCV.put(COLUMN_POSITION_Y, gridItem.getY());
            itemCV.put(COLUMN_HEIGHT, gridItem.getHeight());
            itemCV.put(COLUMN_WIDTH, gridItem.getWidth());
            itemCV.put(COLUMN_GRID_ITEM_TYPE, gridItem.getType());
            itemCV.put(COLUMN_DATA_STRING_1, gridItem.getDS1());
            itemCV.put(COLUMN_DATA_STRING_2, gridItem.getDS2());
            itemCV.put(COLUMN_DATA_INT_1, gridItem.getDI());
            mDB.insert(TABLE_GRID_ITEM, null, itemCV);
        }

        final ContentValues pageCV = new ContentValues();
        pageCV.put(COLUMN_PAGE_ID, gridPage.getID());
        pageCV.put(COLUMN_INDEX, gridPage.getIndex());
        pageCV.put(COLUMN_WIDTH, gridPage.getWidth());
        pageCV.put(COLUMN_HEIGHT, gridPage.getHeight());
        mDB.insert(TABLE_GRID_PAGE, null, pageCV);
    }

    public void dropPage(String pageId) {
        mDB.delete(TABLE_GRID_PAGE, COLUMN_PAGE_ID + "=?", new String[]{pageId});
        mDB.delete(TABLE_GRID_ITEM, COLUMN_PAGE_ID + "=?", new String[]{pageId});
    }

    public void updatePage(ClassicGridPage page) {
        mDB.beginTransaction();
        writePage(page);
        mDB.setTransactionSuccessful();
        mDB.endTransaction();
    }

    public GridFolder insertNewGridFolder(String gridItemId) {
        // Insert a new folder; SQLite will give us a new ID for it
        ContentValues cv = new GridFolder(gridItemId).serialize();
        int id = insertContentValuesAndRetrieveColumnId(TABLE_GRID_FOLDER, cv);

        // Return an unset object to represent this folder
        return new GridFolder(id, gridItemId);
    }


    public void updateGridFolder(GridFolder folder) {
        // Drop all grid folder apps here
        mDB.beginTransaction();
        mDB.delete(
            TABLE_GRID_FOLDER_APPS,
            DatabaseHelper.COLUMN_GRID_FOLDER_ID + "=?",
            new String[] { String.valueOf(folder.getId()) });

        // Re-insert them in the correct order
        List<GridFolderApp> newApps = new ArrayList<>();
        for (int i = 0; i < folder.getApps().size(); i++) {
            GridFolderApp app = folder.getApps().get(i);
            ContentValues cv = app.serialize();
            int id = insertContentValuesAndRetrieveColumnId(TABLE_GRID_FOLDER_APPS, cv);
            newApps.add(
                new GridFolderApp(
                    id, app.getGridFolderId(), i, app.getPackageName(), app.getActivityName()));
        }
        folder.setApps(newApps);

        // Update the root item
        mDB.update(
            TABLE_GRID_FOLDER,
            folder.serialize(),
            COLUMN_ID + "=?",
            new String[] { String.valueOf(folder.getId()) });

        mDB.setTransactionSuccessful();
        mDB.endTransaction();
    }

    public void deleteGridFolder(GridFolder folder) {
        mDB.beginTransaction();
        mDB.delete(
            TABLE_GRID_FOLDER,
            DatabaseHelper.COLUMN_ID + "=?",
            new String[] { String.valueOf(folder.getId()) });
        mDB.delete(
            TABLE_GRID_FOLDER_APPS,
            DatabaseHelper.COLUMN_GRID_FOLDER_ID + "=?",
            new String[] { String.valueOf(folder.getId()) });
        mDB.setTransactionSuccessful();
        mDB.endTransaction();
    }

    // Dock data
    public List<DockItem> getDockPreferences() {
        final List<DockItem> dockItems = new ArrayList<>();
        final Cursor loadItems = mDB.query(
            TABLE_DOCK,
            null,
            null,
            null,
            null,
            null,
            null);
        loadRows:
        {
            if (!loadItems.moveToFirst()) {
                break loadRows;
            }

            final int packageColumn = loadItems.getColumnIndex(COLUMN_PACKAGE);
            final int activityColumn = loadItems.getColumnIndex(COLUMN_ACTIVITY_NAME);
            final int whenToShowColumn = loadItems.getColumnIndex(COLUMN_WHEN_TO_SHOW);
            if (packageColumn == -1 ||
                whenToShowColumn == -1 ||
                activityColumn == -1) {
                break loadRows;
            }

            while (!loadItems.isAfterLast()) {
                String packageName = loadItems.getString(packageColumn);
                String activityName = loadItems.getString(activityColumn);
                int whenToShow = loadItems.getInt(whenToShowColumn);
                dockItems.add(new DockItem(packageName, activityName, whenToShow));
                loadItems.moveToNext();
            }
        }
        loadItems.close();
        return dockItems;
    }

    public void addDockPreference(DockItem item) {
        // If we're remapping app launch backed items, delete the old entries
        if (item.getWhenToShow() != DockItem.DOCK_SHOW_NEVER) {
            mDB.delete(
                TABLE_DOCK,
                COLUMN_WHEN_TO_SHOW + "=?",
                new String[]{String.valueOf(item.getWhenToShow())});
        }

        final ContentValues cv = new ContentValues();
        cv.put(COLUMN_PACKAGE, item.getPackageName());
        cv.put(COLUMN_ACTIVITY_NAME, item.getActivityName());
        cv.put(COLUMN_WHEN_TO_SHOW, item.getWhenToShow());
        mDB.insert(TABLE_DOCK, null, cv);
    }

    public void overwriteHiddenAppDockPreferences(List<DockItem> items) {
        mDB.delete(
            TABLE_DOCK,
            COLUMN_WHEN_TO_SHOW + "=?",
            new String[]{String.valueOf(DockItem.DOCK_SHOW_NEVER)});

        for (DockItem item : items) {
            final ContentValues cv = new ContentValues();
            cv.put(COLUMN_PACKAGE, item.getPackageName());
            cv.put(COLUMN_ACTIVITY_NAME, item.getActivityName());
            cv.put(COLUMN_WHEN_TO_SHOW, item.getWhenToShow());
            mDB.insert(TABLE_DOCK, null, cv);
        }
    }

    // Hidden apps

    public void saveHiddenAppsFromIcons(List<ApplicationIconHideable> apps) {
        mDB.delete(TABLE_HIDDEN_APPS, null, null);
        for (int i = 0; i < apps.size(); i++) {
            if (!apps.get(i).isHidden()) {
                continue;
            }

            ContentValues cv = new ContentValues();
            cv.put(COLUMN_ACTIVITY_NAME, apps.get(i).getActivityName());
            cv.put(COLUMN_PACKAGE, apps.get(i).getPackageName());
            mDB.insert(TABLE_HIDDEN_APPS, null, cv);
        }
    }

    /**
     * Mark an app as hidden.
     *
     * @param activityName The activity.
     * @param packageName  The package.
     */
    public void markAppHidden(String activityName, String packageName) {
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_ACTIVITY_NAME, activityName);
        cv.put(COLUMN_PACKAGE, packageName);
        mDB.insert(TABLE_HIDDEN_APPS, null, cv);
    }

    public Map<Pair<String, String>, Boolean> getHiddenAppsAsMap(boolean hideInternalApps) {
        Map<Pair<String, String>, Boolean> hiddenApps = new HashMap<>();
        final Cursor loadItems =
            mDB.query(TABLE_HIDDEN_APPS, null, null, null, null, null, null);
        loadRows:
        {
            if (!loadItems.moveToFirst()) {
                break loadRows;
            }
            final int packageColumn =
                loadItems.getColumnIndex(COLUMN_PACKAGE);
            final int activityColumn =
                loadItems.getColumnIndex(COLUMN_ACTIVITY_NAME);
            if (packageColumn == -1 || activityColumn == -1) {
                break loadRows;
            }
            while (!loadItems.isAfterLast()) {
                final String packageName = loadItems.getString(packageColumn);
                final String activityName = loadItems.getString(activityColumn);
                hiddenApps.put(new Pair<>(packageName, activityName), true);
                loadItems.moveToNext();
            }
        }
        loadItems.close();
        if (hideInternalApps) {
            hiddenApps.put(new Pair<>(Constants.PACKAGE, HomeActivity.class.getName()), true);
        }
        return hiddenApps;
    }

    public String getPath() {
        return mDB.getPath();
    }

    public void dropAllTables() {
        for (String table : TABLES) {
            mDB.delete(table, null, null);
        }
    }

    private int insertContentValuesAndRetrieveColumnId(String table, ContentValues cv) {
        mDB.beginTransaction();
        long rowId = mDB.insert(table, null, cv);
        Cursor newItemColumnIdCursor =
            mDB.query(
                table,
                new String[] { COLUMN_ID },
                "rowid = ?",
                new String[]{ String.valueOf(rowId) },
                null,
                null,
                null,
                null);
        if (!newItemColumnIdCursor.moveToFirst()) {
            newItemColumnIdCursor.close();
            return -1;
        }
        // Get out the ID from the rowid
        int idColumnIndex = newItemColumnIdCursor.getColumnIndex(COLUMN_ID);
        int id = newItemColumnIdCursor.getInt(idColumnIndex);
        newItemColumnIdCursor.close();
        mDB.setTransactionSuccessful();
        mDB.endTransaction();
        return id;
    }
}
