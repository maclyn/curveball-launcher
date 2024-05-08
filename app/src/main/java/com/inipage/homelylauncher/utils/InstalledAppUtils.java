package com.inipage.homelylauncher.utils;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.state.GestureNavContractSingleton;

/**
 * Helper functions interfacing with Android apps.
 */
public class InstalledAppUtils {

    public enum AppLaunchSource {
        APP_LIST,
        GRID_PAGE,
        DOCK,
        FOLDER
    }

    public static void launchUninstallPackageIntent(Context context, String packageName) {
        try {
            Uri uri = Uri.parse("package:" + packageName);
            Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri);
            uninstallIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            context.startActivity(uninstallIntent);
            GestureNavContractSingleton.INSTANCE.clearComponentLaunch();
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
            GestureNavContractSingleton.INSTANCE.clearComponentLaunch();
        } catch (Exception ignored) {
        }
    }

    /**
     * This is used by the home grid pages and the app list.
     */
    public static void launchApp(
        View anchor,
        String packageName,
        String activityName,
        AppLaunchSource source
    ) {
        final ActivityOptions options =
            ActivityOptions.makeScaleUpAnimation(
                anchor,
                anchor.getWidth() / 2, // + posX filled in from anchor
                anchor.getHeight() / 2, // + posY filled in from anchor
                0, // start from 0 size point
                0);
        // There isn't an obvious way to speed this up, which is is unfortunate because it feels
        // pokey to me
        launchApp(anchor.getContext(), packageName, activityName, options.toBundle(), anchor, source);
    }

    /**
     * Activity options, frustratingly enough, only accepts a View for the anchor, not an arbitrary
     * x/y, so rather than fake an anchor we just construct the Bundle ourselves.
     * This is used by the dock.
     */
    public static boolean launchAppWithIrregularAnchor(
        View view,
        String packageName,
        String activityName,
        AppLaunchSource source
    ) {
        int[] out = new int[2];
        view.getLocationOnScreen(out);

        // See ActivityOptions#toBundle() for reference on these magic values
        final Bundle bundle = new Bundle();
        bundle.putString("android:activity.packageName", view.getContext().getPackageName());
        bundle.putInt("android:activity.animType", 2);
        bundle.putInt("android:activity.animStartX", out[0]);
        bundle.putInt("android:activity.animStartY", out[1]);
        bundle.putInt("android:activity.animWidth", view.getWidth());
        bundle.putInt("android:activity.animHeight", view.getHeight());
        return launchApp(view.getContext(), packageName, activityName, bundle, view, source);
    }

    private static boolean launchApp(
        Context context,
        String packageName,
        String activityName,
        @Nullable Bundle b,
        View sourceView,
        AppLaunchSource source) {
        try {
            final Intent launchIntent = new Intent();
            launchIntent.setComponent(new ComponentName(packageName, activityName));
            launchIntent.setAction(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_DEFAULT);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent, b);

            if (source == AppLaunchSource.GRID_PAGE) {
                int[] pts = new int[2];
                sourceView.getLocationOnScreen(pts);
                final RectF position =
                    new RectF(
                        pts[0],
                        pts[1],
                        pts[0] + sourceView.getWidth(),
                        pts[1] + sourceView.getHeight());
                GestureNavContractSingleton.INSTANCE.onAppLaunchRequest(
                    packageName,
                    activityName,
                    position);
            } else {
                GestureNavContractSingleton.INSTANCE.clearComponentLaunch();
            }

            return true;
        } catch (Exception appNotInstalled) {
            Toast.makeText(context, R.string.cant_start, Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
