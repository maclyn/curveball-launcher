package com.inipage.homelylauncher.caches;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.SizeValAttribute;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

/**
 * Blocking IconCache.
 * <p>
 * TODO: Dirty hack: synchronizing all the methods, yikes
 */
public class IconCacheSync {

    private static final String TAG = "IconCacheSync";
    private static IconCacheSync s_INSTANCE;
    private final Resources mBaseResources;
    private final PackageManager mPackageManager;
    private final String mPackageName;
    private final Map<IconKey, Bitmap> mAppIconMap;
    private final Map<IconKey, Bitmap> mRemoteResourceMap;
    private final Map<String, Set<IconKey>> mPackageToIconKeyMap;
    private final Map<String, Resources> mRemoteApplicationResourcesMap;
    private final Map<String, Bitmap> mLocalResourceMap;
    private final Bitmap mDummyBitmap;
    private final IconKey mTempKey;
    @SizeValAttribute(64)
    private final int mDefaultSize = intValue();

    private IconCacheSync(Context context) {
        AttributeApplier.applyDensity(this, context);
        mBaseResources = context.getResources();
        mPackageManager = context.getPackageManager();
        mPackageName = context.getPackageName();
        mAppIconMap = new HashMap<>();
        mRemoteResourceMap = new HashMap<>();
        mPackageToIconKeyMap = new HashMap<>();
        mRemoteApplicationResourcesMap = new HashMap<>();
        mLocalResourceMap = new HashMap<>();
        mTempKey = new IconKey();

        // Create a dummy bitmap so we don't have to throw exceptions
        final int roughAppSize = mBaseResources.getDisplayMetrics().widthPixels / 4;
        mDummyBitmap = Bitmap.createBitmap(roughAppSize, roughAppSize, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(mDummyBitmap);
        final Paint p = new Paint();
        p.setColor(Color.WHITE);
        p.setAntiAlias(true);
        c.drawCircle(roughAppSize / 2F, roughAppSize / 2F, roughAppSize / 2F, p);
    }

    public static IconCacheSync getInstance(Context context) {
        if (s_INSTANCE == null) {
            s_INSTANCE = new IconCacheSync(context);
        }
        return s_INSTANCE;
    }

    public synchronized Bitmap getActivityIcon(String packageName, String activityName) {
        mTempKey.first = packageName;
        mTempKey.second = activityName;
        @Nullable final Bitmap value = mAppIconMap.get(mTempKey);
        if (value != null) {
            Log.v(TAG, "Cache hit for app icon=" + packageName);
            return value;
        }
        Log.v(TAG, "Cache miss for app icon=" + packageName + ";" + activityName);
        final IconKey key = mTempKey.clone();
        Bitmap bitmap;
        try {
            bitmap = getBitmapFromDrawable(
                mPackageManager.getActivityIcon(new ComponentName(packageName, activityName)));
        } catch (OutOfMemoryError | PackageManager.NameNotFoundException e) {
            bitmap = mDummyBitmap;
        }
        mAppIconMap.put(key, bitmap);
        if (mPackageToIconKeyMap.containsKey(packageName)) {
            mPackageToIconKeyMap.get(packageName).add(key);
        } else {
            final Set<IconKey> keySet = new HashSet<>();
            keySet.add(key);
            mPackageToIconKeyMap.put(packageName, keySet);
        }
        return bitmap;
    }

    public synchronized Bitmap getBitmapFromDrawable(Drawable d) {
        if (d instanceof BitmapDrawable) {
            return ((BitmapDrawable) d).getBitmap();
        }
        final Bitmap toDraw =
            Bitmap.createBitmap(mDefaultSize, mDefaultSize, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(toDraw);
        d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        d.draw(canvas);
        return toDraw;
    }

    public synchronized Bitmap getLocalResource(int resourceId) {
        @Nullable final Bitmap value =
            mLocalResourceMap.get(String.valueOf(resourceId));
        if (value != null) {
            Log.v(TAG, "Cache hit for local resource=" + resourceId);
            return value;
        }
        Log.v(TAG, "Cache miss for local resource=" + resourceId);
        Bitmap bitmap;
        try {
            bitmap = getBitmapFromDrawable(mBaseResources.getDrawable(resourceId));
        } catch (OutOfMemoryError outOfMemoryError) {
            bitmap = mDummyBitmap;
        }
        mLocalResourceMap.put(String.valueOf(resourceId), bitmap);
        return bitmap;
    }

    public synchronized Bitmap getNamedResource(String packageName, String resourceName) {
        mTempKey.first = packageName;
        mTempKey.second = resourceName;
        @Nullable final Bitmap value = mRemoteResourceMap.get(mTempKey);
        if (value != null) {
            Log.v(TAG, "Cache hit for named resource=" + resourceName);
            return value;
        }
        Log.v(TAG, "Cache miss for named resource=" + resourceName);
        final IconKey key = mTempKey.clone();
        Bitmap bitmap;
        try {
            Resources resources = mRemoteApplicationResourcesMap.get(packageName);
            if (resources == null) {
                if (packageName.equals(mPackageName)) {
                    resources = mBaseResources;
                } else {
                    resources = mPackageManager.getResourcesForApplication(packageName);
                }
                mRemoteApplicationResourcesMap.put(packageName, resources);
            }
            final int resourceId =
                resources.getIdentifier(resourceName, "drawable", packageName);
            bitmap = getBitmapFromDrawable(resources.getDrawable(resourceId));
        } catch (OutOfMemoryError |
            Resources.NotFoundException |
            PackageManager.NameNotFoundException outOfMemoryError) {
            bitmap = mDummyBitmap;
        }
        mRemoteResourceMap.put(key, bitmap);
        if (mPackageToIconKeyMap.containsKey(packageName)) {
            mPackageToIconKeyMap.get(packageName).add(key);
        } else {
            final Set<IconKey> keySet = new HashSet<>();
            keySet.add(key);
            mPackageToIconKeyMap.put(packageName, keySet);
        }
        return bitmap;
    }

    public synchronized void clearCache() {
        mAppIconMap.clear();
        mLocalResourceMap.clear();
        mRemoteResourceMap.clear();
        mPackageToIconKeyMap.clear();
        mRemoteApplicationResourcesMap.clear();
    }

    public synchronized void clearCacheForPackage(String packageName) {
        mRemoteApplicationResourcesMap.remove(packageName);
        if (!mPackageToIconKeyMap.containsKey(packageName)) {
            return;
        }
        for (IconKey key : mPackageToIconKeyMap.get(packageName)) {
            mAppIconMap.remove(key);
            mRemoteResourceMap.remove(key);
        }
    }

    public synchronized Bitmap getDummyBitmap() {
        return mDummyBitmap;
    }

    private static class IconKey {
        public String first;
        public String second;

        public IconKey() {
            first = second = "";
        }

        @Override
        public int hashCode() {
            return (first + second).hashCode();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof IconKey)) {
                return false;
            }
            IconKey other = (IconKey) obj;
            return first.equals(other.first) && second.equals(other.second);
        }

        @NotNull
        @Override
        public IconKey clone() {
            IconKey copy = new IconKey();
            copy.first = first;
            copy.second = second;
            return copy;
        }
    }
}
