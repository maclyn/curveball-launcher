package com.inipage.homelylauncher.utils;

import com.google.gson.Gson;

public class Constants {

    public static final String DISABLED_CALENDARS_PREF = "disabled_calendars_pref";

    public static final String HAS_SHOWN_NEW_USER_EXPERIENCE = "new_user_pref";

    public static final String DEV_MODE_PREF = "dev_mode_pref";

    // Icon pack prefs
    public static final String HAS_ICON_PACK_SET_PREF = "has_icon_pack_set_pref";
    public static final String SELECTED_ICON_PACK_PACKAGE_PREF = "icon_package_pkg_pref";
    public static final String MONOCHROME_DOCK_PREF = "mono_dock_pref";

    public static final String PACKAGE = "com.inipage.homelylauncher";
    public static final String DEFAULT_FOLDER_ICON = "ic_folder_white_48dp";

    public static final String INTENT_ACTION_RESTART = "com.inipage.homelylauncher.RESTART_HOME";

    public static final String SHARED_PREFS_IMPORT_PATH = "tmp_prefs.xml";

    public static final String FONT_OVERRIDES_PATH = "font_overrides";

    public static final String GRID_FONT_PATH = "grid_font.ttf";

    public static final String LIST_FONT_PATH = "list_font.ttf";

    public static final String DOCK_FONT_PATH = "dock_font.ttf";

    public static final Gson DEFAULT_GSON = new Gson();

    public static final boolean DEBUG_RENDER = false;

    // More than this, and widgets start to freak out
    public static final int DEFAULT_COLUMN_COUNT = 5;
    public static final int DEFAULT_MAX_ROW_COUNT = 6;

    // Each cell of the homescreen isn't actually square -- we have to apply this factor,
    // or widgets aren't going to fit right...
    public static final float DEFAULT_WIDTH_TO_HEIGHT_SCALAR = 1.5F;

    // Width-to-height scalar for smaller screens; widgets don't fit super great at this factor,
    // but it squeezes an extra row on for a squished screen
    public static final float SQUAT_WIDTH_TO_HEIGHT_SCALAR = 1.15F;

    // Values for input devices that get special treatment
    public static final String VIRTUAL_TITAN_POCKET_SCROLLPAD_INPUT_DEVICE_NAME = "mtk-pad";
    public static final String PHYSICAL_TITAN_POCKET_KEYBOARD_INPUT_DEVICE_NAME = "aw9523-key";
}
