package com.inipage.homelylauncher.persistence;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.utils.LifecycleLogUtils;

/**
 * Raw database management.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    // Database basics
    static final String TAG = "DatabaseHelper";
    static final String DATABASE_NAME = "database.db";
    static final int DATABASE_VERSION = 17;

    // Common columns
    static final String COLUMN_ID = "_id";

    // Gesture favorites table
    static final String TABLE_ROWS = "rows_table";
    static final String COLUMN_DATA = "data_string";
    static final String COLUMN_GRAPHIC = "graphic_res";
    static final String COLUMN_GRAPHIC_PACKAGE = "graphic_package";
    static final String COLUMN_ORDER = "ordering";
    static final String COLUMN_TITLE = "title";

    // Hidden apps table
    static final String TABLE_HIDDEN_APPS = "hidden_apps_table";
    static final String COLUMN_PACKAGE = "package_name";
    static final String COLUMN_ACTIVITY_NAME = "activity_name";

    // Dock table
    static final String TABLE_DOCK = "smartapps_table";
    // Re-uses: COLUMN_PACKAGE, COLUMN_ACTIVITY_NAME, COLUMN_WIDGET_ID
    static final String COLUMN_WHEN_TO_SHOW = "when_to_show";
    static final String COLUMN_WIDGET_ID = "widget_id";

    // Grid pages
    // Re-uses: COLUMN_INDEX
    static final String TABLE_GRID_PAGE = "grid_page_table";
    static final String COLUMN_PAGE_ID = "page_id";
    static final String COLUMN_INDEX = "idx";
    static final String COLUMN_HEIGHT = "height";
    static final String COLUMN_WIDTH = "width";

    // Grid item table
    // Re-uses: HEIGHT, WIDTH, PAGE_ID
    static final String TABLE_GRID_ITEM = "grid_item_table";
    static final String COLUMN_ITEM_ID = "item_id";
    static final String COLUMN_GRID_ITEM_TYPE = "grid_item_type";
    static final String COLUMN_POSITION_X = "position_x";
    static final String COLUMN_POSITION_Y = "position_y";
    static final String COLUMN_DATA_STRING_1 = "ds1";
    static final String COLUMN_DATA_STRING_2 = "ds2";
    static final String COLUMN_DATA_INT_1 = "di1";

    // Vertical grid page
    // Re-uses: HEIGHT, WIDTH
    static final String TABLE_VERTICAL_GRID_PAGE = "vertical_grid_page_table";

    // Vertical grid item
    // Re-uses: HEIGHT, WIDTH, ITEM_IT, GRID_ITEM_TYPE, POSITION_X, POSITION_Y, DATA_STRING_1, DATA_STRING_2, DATA_INT_1
    static final String TABLE_VERTICAL_GRID_ITEM = "vertical_grid_item_table";

    static final String[] TABLES = new String[]{
        TABLE_GRID_PAGE,
        TABLE_GRID_ITEM,
        TABLE_VERTICAL_GRID_PAGE,
        TABLE_VERTICAL_GRID_ITEM,
        TABLE_HIDDEN_APPS,
        TABLE_ROWS,
        TABLE_DOCK
    };
    private static final String ROWS_TABLE_CREATE = "create table "
        + TABLE_ROWS +
        "(" + COLUMN_ID + " integer primary key autoincrement, "
        + COLUMN_DATA + " text not null, "
        + COLUMN_GRAPHIC + " text not null, "
        + COLUMN_GRAPHIC_PACKAGE + " text not null, "
        + COLUMN_ORDER + " integer not null, "
        + COLUMN_TITLE + " text not null);";
    private static final String HIDDEN_APPS_TABLE_CREATE = "create table "
        + TABLE_HIDDEN_APPS +
        "(" + COLUMN_ID + " integer primary key autoincrement, "
        + COLUMN_PACKAGE + " text not null, "
        + COLUMN_ACTIVITY_NAME + " text not null);";
    private static final String DOCK_TABLE_CREATE = "create table "
        + TABLE_DOCK +
        "(" + COLUMN_ID + " integer primary key autoincrement, "
        + COLUMN_PACKAGE + " text not null, "
        + COLUMN_ACTIVITY_NAME + " text not null, "
        + COLUMN_WHEN_TO_SHOW + " integer not null" + ");";
    private static final String GRID_PAGE_TABLE_CREATE = "CREATE TABLE "
        + TABLE_GRID_PAGE
        + "(" + COLUMN_ID + " INTEGER PRIMARY KEY autoincrement, "
        + COLUMN_PAGE_ID + " text not null, "
        + COLUMN_INDEX + " integer not null, "
        + COLUMN_WIDTH + " integer not null, "
        + COLUMN_HEIGHT + " integer not null);";
    private static final String VERTICAL_GRID_PAGE_TABLE_CREATE = "CREATE TABLE "
        + TABLE_VERTICAL_GRID_PAGE
        + "(" + COLUMN_ID + " INTEGER PRIMARY KEY autoincrement, "
        + COLUMN_WIDTH + " integer not null, "
        + COLUMN_HEIGHT + " integer not null);";
    private static final String GRID_ITEM_TABLE_CREATE = "CREATE TABLE "
        + TABLE_GRID_ITEM
        + "(" + COLUMN_ID + " INTEGER PRIMARY KEY autoincrement,"
        + COLUMN_ITEM_ID + " text not null, "
        + COLUMN_POSITION_X + " integer not null, "
        + COLUMN_POSITION_Y + " integer not null, "
        + COLUMN_PAGE_ID + " text not null, "
        + COLUMN_HEIGHT + " integer not null, "
        + COLUMN_WIDTH + " integer not null, "
        + COLUMN_GRID_ITEM_TYPE + " integer not null, "
        + COLUMN_DATA_STRING_1 + " text, "
        + COLUMN_DATA_STRING_2 + " text, "
        + COLUMN_DATA_INT_1 + " integer);";
    private static final String VERTICAL_GRID_ITEM_TABLE_CREATE = "CREATE TABLE "
        + TABLE_VERTICAL_GRID_ITEM
        + "(" + COLUMN_ID + " INTEGER PRIMARY KEY autoincrement,"
        + COLUMN_ITEM_ID + " text not null, "
        + COLUMN_POSITION_X + " integer not null, "
        + COLUMN_POSITION_Y + " integer not null, "
        + COLUMN_HEIGHT + " integer not null, "
        + COLUMN_WIDTH + " integer not null, "
        + COLUMN_GRID_ITEM_TYPE + " integer not null, "
        + COLUMN_DATA_STRING_1 + " text, "
        + COLUMN_DATA_STRING_2 + " text, "
        + COLUMN_DATA_INT_1 + " integer);";

    private static final String[] CREATION_BLOCKS = {
        GRID_PAGE_TABLE_CREATE,
        GRID_ITEM_TABLE_CREATE,
        VERTICAL_GRID_ITEM_TABLE_CREATE,
        HIDDEN_APPS_TABLE_CREATE,
        ROWS_TABLE_CREATE,
        DOCK_TABLE_CREATE,
    };

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public DatabaseHelper(Context context, String arbitraryPath) {
        super(context, arbitraryPath, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 15 -> 16/17 migration
        if (oldVersion == 15) {
            LifecycleLogUtils.logEvent(LifecycleLogUtils.LogType.LOG, TAG + " upgrading from 15 to newer");
            db.execSQL(VERTICAL_GRID_ITEM_TABLE_CREATE);
            db.execSQL(VERTICAL_GRID_PAGE_TABLE_CREATE);
            return;
        }

        // 16 -> 17 migration
        if (oldVersion == 16) {
            LifecycleLogUtils.logEvent(LifecycleLogUtils.LogType.LOG, TAG + " upgrading from 16 to newer");
            db.execSQL(VERTICAL_GRID_PAGE_TABLE_CREATE);
            return;
        }

        // Anything else, start from scratch
        LifecycleLogUtils.logEvent(LifecycleLogUtils.LogType.ERROR, TAG + " upgrading unexpected path");
        for (String table : TABLES) {
            try {
                db.execSQL("DROP TABLE " + table);
            } catch (Exception ignored) {
            }
        }
        createTables(db);
    }

    private void createTables(SQLiteDatabase db) {
        for (String creationBlock : CREATION_BLOCKS) {
            db.execSQL(creationBlock);
        }
    }

    @Nullable
    public String findProblemsWithDatabase() {
        try {
            final SQLiteDatabase database = getWritableDatabase();
            final Cursor c = database.query(TABLE_GRID_PAGE, null, null, null, null, null, null);
            if (c.getCount() < 1) {
                c.close();
                database.close();
                return "No grid pages were in this database.";
            }
            c.close();
            database.close();
            return null;
        } catch (Exception e) {
            return "This database couldn't be opened.";
        }
    }
}
