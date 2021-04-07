package com.inipage.homelylauncher.utils;

import android.annotation.SuppressLint;
import android.content.Context;

import java.lang.reflect.Method;

public class StatusBarUtils {

    public static void expandStatusBar(Context context) {
        try {
            @SuppressLint("WrongConstant") final Object service = context.getSystemService(
                "statusbar");
            Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");
            Method expand = statusbarManager.getMethod("expandNotificationsPanel");
            expand.invoke(service);
        } catch (Exception ignored) {
        }
    }
}
