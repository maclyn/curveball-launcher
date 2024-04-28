package com.inipage.homelylauncher.utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.BuildConfig;
import com.inipage.homelylauncher.persistence.PrefsHelper;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DebugLogUtils {

    public static final String TAG_DRAG_OFFSET = "tag_drag_offset";
    public static final String TAG_ICON_CASCADE = "tag_icon_cascade";
    public static final String TAG_PAGE_SCROLL = "tag_page_scroll";
    public static final String TAG_CUSTOM_TOUCHEVENTS = "tag_custom_touchevents";
    public static final String TAG_GRID_HANDLE = "tag_grid_handle";
    public static final String TAG_DECOR_DRAGGER = "tag_decor_dragger";
    public static final String TAG_WEATHER_LOADING = "tag_weather_loading";
    public static final String TAG_WALLPAPER_OFFSET = "tag_wallpaper_offset";
    public static final String TAG_BOTTOM_SHEET = "tag_bottom_sheet";
    public static final String TAG_POCKET_ANIMATION = "tag_pocket_animation";
    public static final String TAG_VIRTUAL_TRACKPAD = "tag_virtual_trackpad";

    private static final String NEEDLED = TAG_CUSTOM_TOUCHEVENTS;

    public static void complain(Context context, String complaint) {
        if (!PrefsHelper.isDevMode() && !BuildConfig.DEBUG) {
            return;
        }
        Toast.makeText(context, complaint, Toast.LENGTH_SHORT).show();
    }

    public static void complain(View view, String complaint) {
        complain(view.getContext(), complaint);
    }

    public static void complain(WeakReference<Activity> ref, String complaint) {
        @Nullable Context context = ref.get();
        if (context == null) {
            return;
        }
        complain(context, complaint);
    }

    public static void needle(String tag, Object... out) {
        String[] array = Arrays.stream(out).map(Object::toString).toArray(String[]::new);
        needle(tag, array);
    }

    public static void needle(String tag, String... out) {
        needle(tag, 0, null, out);
    }

    public static void needle(
        String tag, int skipCount, @Nullable String skipString, String... out) {
        if (!tag.equals(NEEDLED) || !BuildConfig.DEBUG) {
            return;
        }

        StringBuilder output = new StringBuilder();
        StackTraceElement[] stack = new Exception("").getStackTrace();
        @Nullable StackTraceElement element = null;
        for (int i = 2 + skipCount; i < stack.length; i++) {
            final String className = stack[i].getClassName().toLowerCase();
            if (skipString != null && className.contains(skipString.toLowerCase())) {
                continue;
            }
            if (className.contains("log")) {
                continue;
            }
            if (className.contains("debuglogutils")) {
                continue;
            }
            element = stack[i];
            break;
        }
        if (element != null) {
            final String className = element.getClassName().lastIndexOf(".") > -1
                                     ?
                                     element.getClassName()
                                         .substring(element.getClassName().lastIndexOf(".") + 1)
                                     :
                                     element.getClassName();
            output.append('<');
            output.append(className);
            output.append('.');
            output.append(element.getMethodName());
            output.append("() L");
            output.append(element.getLineNumber());
            output.append('>');
        }

        for (String o : out) {
            output.append(" ");
            output.append(o);
            output.append(" ");
        }
        Log.d(NEEDLED, output.toString());
    }
}
