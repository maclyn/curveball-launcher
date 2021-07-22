package com.inipage.homelylauncher;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.utils.LifecycleLogUtils;

import static com.inipage.homelylauncher.utils.LifecycleLogUtils.LogType.ERROR;
import static com.inipage.homelylauncher.utils.LifecycleLogUtils.LogType.LIFECYCLE_CHANGE;

public class ApplicationClass extends Application {


    private final ActivityLifecycleCallbacks mActivityLifecycleCallbacks =
        new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(
                @NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                LifecycleLogUtils.logEvent(
                    LIFECYCLE_CHANGE, "onActivityCreated " + activity.getClass().getSimpleName());
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(
                @NonNull Activity activity,
                @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                LifecycleLogUtils.logEvent(
                    LIFECYCLE_CHANGE, "onActivityDestroyed " + activity.getClass().getSimpleName());
            }
        };
    private final UncaughtHandler mUncaughtExceptionHandler =
        new UncaughtHandler() {
            @Nullable
            private Thread.UncaughtExceptionHandler mDefaultHandler;

            @Override
            public void setDefaultHandler(
                @Nullable Thread.UncaughtExceptionHandler defaultHandler) {
                mDefaultHandler = defaultHandler;
            }

            @Override
            public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                LifecycleLogUtils.logEvent(ERROR, "Uncaught exception: " + e);
                LifecycleLogUtils.logEvent(ERROR, Log.getStackTraceString(e));
                if (mDefaultHandler != null) {
                    LifecycleLogUtils.closeLog(); // We're about to crash, presumably...
                    mDefaultHandler.uncaughtException(t, e);
                }
            }
        };

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(mActivityLifecycleCallbacks);
        // This runs on the main thread, so we can just set this up here...
        mUncaughtExceptionHandler.setDefaultHandler(Thread.getDefaultUncaughtExceptionHandler());
        Thread.setDefaultUncaughtExceptionHandler(mUncaughtExceptionHandler);
        LifecycleLogUtils.openLog(this);
        LifecycleLogUtils.logEvent(LIFECYCLE_CHANGE, "Application started");

        // The following objects map 1:1 with the lifecycle of the process, and thus,
        // ApplicationClass
        DatabaseEditor.seed(this);
        AppInfoCache.seed(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        unregisterActivityLifecycleCallbacks(mActivityLifecycleCallbacks);
        LifecycleLogUtils.logEvent(LIFECYCLE_CHANGE, "Application terminated");
        LifecycleLogUtils.closeLog();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LifecycleLogUtils.logEvent(
            LIFECYCLE_CHANGE,
            "Configuration changed: " + newConfig.screenWidthDp + "x" + newConfig.screenHeightDp);
    }

    private interface UncaughtHandler extends Thread.UncaughtExceptionHandler {
        void setDefaultHandler(@Nullable Thread.UncaughtExceptionHandler defaultHandler);
    }
}
