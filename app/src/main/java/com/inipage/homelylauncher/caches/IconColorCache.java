package com.inipage.homelylauncher.caches;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.palette.graphics.Palette;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A cache of primary colors pulled out of a bitmap.
 */
public class IconColorCache {

    private static final String TAG = "IconColorCache";
    private static final int s_DEFAULT_COLOR = Color.WHITE;
    private static IconColorCache s_INSTANCE;
    private final WeakHashMap<Bitmap, Integer> mColorMap;
    private final ThreadPoolExecutor mThreadPoolExecutor;

    private IconColorCache() {
        mColorMap = new WeakHashMap<>();
        final BlockingQueue<Runnable> decodeWorkQueue = new LinkedBlockingQueue<>();
        mThreadPoolExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            1,
            TimeUnit.SECONDS,
            decodeWorkQueue);
    }

    public static IconColorCache getInstance() {
        if (s_INSTANCE == null) {
            s_INSTANCE = new IconColorCache();
        }
        return s_INSTANCE;
    }

    public int getColorForBitmap(Context context, Bitmap b, ColorFoundCallback callback) {
        if (b == IconCacheSync.getInstance(context).getDummyBitmap()) {
            // Log.v(TAG, "Cache hit for dummy bitmap");
            return s_DEFAULT_COLOR;
        }
        if (mColorMap.containsKey(b)) {
            // Log.v(TAG, "Cache hit for icon color");
            return mColorMap.get(b);
        }
        // Log.v(TAG, "Cache miss for icon color");
        mThreadPoolExecutor.execute(new ColorFetchRunnable(mColorMap, b, callback));
        return Color.WHITE;
    }

    public void clearCache() {
        mColorMap.clear();
    }

    public interface ColorFoundCallback {
        void onColorFound(int color);
    }

    private static class ColorFetchRunnable implements Runnable {
        private final Map<Bitmap, Integer> mColorMap;
        private final Bitmap mBitmap;
        private final ColorFoundCallback mCallback;

        public ColorFetchRunnable(
            Map<Bitmap, Integer> colorMap,
            Bitmap b,
            ColorFoundCallback callback) {
            mColorMap = colorMap;
            mBitmap = b;
            mCallback = callback;
        }

        @Override
        public void run() {
            final Palette p = Palette.from(mBitmap).generate();
            int choice = s_DEFAULT_COLOR;
            if (p.getVibrantSwatch() != null) {
                choice = p.getVibrantSwatch().getRgb();
            } else if (p.getLightVibrantSwatch() != null) {
                choice = p.getLightVibrantSwatch().getRgb();
            } else if (p.getDarkVibrantSwatch() != null) {
                choice = p.getDarkVibrantSwatch().getRgb();
            } else if (p.getLightMutedSwatch() != null) {
                choice = p.getLightMutedSwatch().getRgb();
            } else if (p.getDarkMutedSwatch() != null) {
                choice = p.getDarkMutedSwatch().getRgb();
            }
            mColorMap.put(mBitmap, choice);
            if (choice == s_DEFAULT_COLOR) {
                return;
            }
            if (mCallback != null) {
                mCallback.onColorFound(choice);
            }
        }
    }
}
