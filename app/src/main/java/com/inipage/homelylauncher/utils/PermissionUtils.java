package com.inipage.homelylauncher.utils;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

public class PermissionUtils {
    public static boolean checkPermission(String permission, Context context) {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED;
    }
}
