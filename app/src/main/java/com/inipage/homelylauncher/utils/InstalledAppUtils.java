package com.inipage.homelylauncher.utils;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import com.inipage.homelylauncher.R;

/**
 * Helper functions interfacing with Android apps.
 */
public class InstalledAppUtils {

    public static void launchUninstallPackageIntent(Context context, String packageName) {
        try {
            Uri uri = Uri.parse("package:" + packageName);
            Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri);
            uninstallIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            context.startActivity(uninstallIntent);
        } catch (Exception ignored) {
        }
    }

    public static boolean canUninstallPackage(Context context, String packageName) {
        try {
            final int flags =
                context.getPackageManager().getApplicationInfo(packageName, 0).flags;
            return (flags & ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (PackageManager.NameNotFoundException notFoundException) {
            return false;
        }
    }

    public static void launchPackageInfoIntent(Context context, String packageName) {
        try {
            Uri uri = Uri.parse("package:" + packageName);
            Intent appInfoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            appInfoIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            appInfoIntent.setData(uri);
            context.startActivity(appInfoIntent);
        } catch (Exception ignored) {
        }
    }

    public static void launchApp(View anchor, String packageName, String activityName) {
        final ActivityOptions options = ActivityOptions.makeScaleUpAnimation(
            anchor, 0, 0, anchor.getWidth(), anchor.getHeight());
        launchApp(anchor.getContext(), packageName, activityName, options.toBundle());
    }

    public static boolean launchApp(
        Context context,
        String packageName,
        String activityName,
        @androidx.annotation.Nullable Bundle b) {
        try {
            final Intent launchIntent = new Intent();
            launchIntent.setComponent(new ComponentName(packageName, activityName));
            launchIntent.setAction(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_DEFAULT);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent, b);
            return true;
        } catch (Exception appNotInstalled) {
            Toast.makeText(context, R.string.cant_start, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * Activity options, frustratingly enough, only accepts a View for the anchor, not an arbitrary
     * x/y, so rather than fake an anchor we just construct the Bundle ourselves.
     */
    public static boolean launchApp(
        int startX,
        int startY,
        int width,
        int height,
        Context context,
        String packageName,
        String activityName) {
        // See ActivityOptions#toBundle() for reference on these magic values
        final Bundle bundle = new Bundle();
        bundle.putString("android:activity.packageName", context.getPackageName());
        bundle.putInt("android:activity.animType", 2);
        bundle.putInt("android:activity.animStartX", startX);
        bundle.putInt("android:activity.animStartY", startY);
        bundle.putInt("android:activity.animWidth", width);
        bundle.putInt("android:activity.animHeight", height);
        return launchApp(context, packageName, activityName, bundle);
    }
}
