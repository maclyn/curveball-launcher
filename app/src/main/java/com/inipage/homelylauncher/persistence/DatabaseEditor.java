package com.inipage.homelylauncher.persistence;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.google.common.collect.ImmutableList;
import com.inipage.homelylauncher.HomeActivity;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.model.DockItem;
import com.inipage.homelylauncher.model.GridItem;
import com.inipage.homelylauncher.model.GridPage;
import com.inipage.homelylauncher.model.SwipeApp;
import com.inipage.homelylauncher.model.SwipeFolder;
import com.inipage.homelylauncher.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_ACTIVITY_NAME;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_DATA;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_DATA_INT_1;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_DATA_STRING_1;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_DATA_STRING_2;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_GRAPHIC;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_GRAPHIC_PACKAGE;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_GRID_ITEM_TYPE;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_HEIGHT;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_INDEX;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_ITEM_ID;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_ORDER;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_PACKAGE;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_PAGE_ID;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_POSITION_X;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_POSITION_Y;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_TITLE;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_WHEN_TO_SHOW;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.COLUMN_WIDTH;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLES;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLE_DOCK;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLE_GRID_ITEM;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLE_GRID_PAGE;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLE_HIDDEN_APPS;
import static com.inipage.homelylauncher.persistence.DatabaseHelper.TABLE_ROWS;

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
    public List<GridPage> getGridPages() {
        final Map<String, GridPage> pageIdToPage = new HashMap<>();
        Cursor cursor = mDB.rawQuery(
            "SELECT * FROM " +
                TABLE_GRID_PAGE +
                " ORDER BY " + COLUMN_INDEX + " desc",
            null);
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
                pageIdToPage.put(id, new GridPage(new ArrayList<>(), id, index, width, height));
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
                final GridItem gridItem = new GridItem(
                    cursor.getString(itemIdColumn),
                    cursor.getString(pageIdColumn),
                    cursor.getInt(xColumn),
                    cursor.getInt(yColumn),
                    cursor.getInt(widthColumn),
                    cursor.getInt(heightColumn),
                    cursor.getInt(typeColumn),
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
            .sorted((o1, o2) -> o1.getValue().getIndex() - o2.getValue().getIndex())
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    public void saveGridPages(List<GridPage> gridPages) {
        mDB.beginTransaction();
        mDB.delete(TABLE_GRID_PAGE, null, null);
        mDB.delete(TABLE_GRID_ITEM, null, null);
        for (GridPage gridPage : gridPages) {
            writePage(gridPage);
        }
        mDB.setTransactionSuccessful();
        mDB.endTransaction();
    }

    private void writePage(GridPage gridPage) {
        dropPage(gridPage.getID());

        for (GridItem gridItem : gridPage.getItems()) {
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

    public void updatePage(GridPage page) {
        mDB.beginTransaction();
        writePage(page);
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

    // Gesture apps

    public List<SwipeFolder> getGestureFavorites() {
        List<SwipeFolder> samples = new ArrayList<>(10);
        loadRows:
        {
            Cursor loadItems = mDB.query(
                TABLE_ROWS, null, null, null, null, null, null);
            if (loadItems.moveToFirst()) {
                int dataColumn = loadItems.getColumnIndex(COLUMN_DATA);
                int graphicColumn = loadItems.getColumnIndex(COLUMN_GRAPHIC);
                int graphicPackageColumn =
                    loadItems.getColumnIndex(COLUMN_GRAPHIC_PACKAGE);
                int titleColumn = loadItems.getColumnIndex(COLUMN_TITLE);
                if (dataColumn == -1 ||
                    graphicColumn == -1 ||
                    graphicPackageColumn == -1 ||
                    titleColumn == -1) {
                    loadItems.close();
                    break loadRows;
                }
                while (!loadItems.isAfterLast()) {
                    String data = loadItems.getString(dataColumn);
                    String graphicPackage = loadItems.getString(graphicPackageColumn);
                    String title = loadItems.getString(titleColumn);
                    String graphic = loadItems.getString(graphicColumn);
                    // Generate package/activity pairs by splitting data
                    ImmutableList.Builder<SwipeApp> cardApps = new ImmutableList.Builder<>();
                    String[] pairs = data.split(",");
                    for (String pair : pairs) {
                        String[] packAndAct = pair.split("\\|");
                        cardApps.add(new SwipeApp(packAndAct[0], packAndAct[1]));
                    }
                    samples.add(new SwipeFolder(title, graphicPackage, graphic, cardApps.build()));
                    loadItems.moveToNext();
                }
            }
            loadItems.close();
        }
        return samples;
    }

    public void saveGestureFavorites(List<SwipeFolder> swipeFolders) {
        mDB.delete(TABLE_ROWS, null, null);
        for (int i = 0; i < swipeFolders.size(); i++) {
            ContentValues cv = new ContentValues();
            cv.put(COLUMN_GRAPHIC, swipeFolders.get(i).getDrawableName());
            cv.put(COLUMN_GRAPHIC_PACKAGE, swipeFolders.get(i).getDrawablePackage());
            cv.put(COLUMN_TITLE, swipeFolders.get(i).getTitle());
            cv.put(COLUMN_ORDER, i);
            String data;
            StringBuilder dataBuilder = new StringBuilder();
            for (SwipeApp app : swipeFolders.get(i).getShortcutApps()) {
                dataBuilder
                    .append(app.getComponent().first)
                    .append("|")
                    .append(app.getComponent().second)
                    .append(",");
            }
            data = dataBuilder.toString();
            data = data.substring(0, data.length() - 1);
            cv.put(COLUMN_DATA, data);
            mDB.insert(TABLE_ROWS, null, cv);
        }
    }

    public String getPath() {
        return mDB.getPath();
    }

    public void dropAllTables() {
        for (String table : TABLES) {
            mDB.delete(table, null, null);
        }
    }
}
