package com.inipage.homelylauncher.swipefolders;

import android.content.Context;
import android.graphics.Bitmap;

import com.inipage.homelylauncher.caches.IconColorCache;

/**
 * Helper for content rendered in an {@linkplain AccordionRow}.
 */
public abstract class RowContent {

    public abstract String getLabel(Context c);

    protected int getTint(Context context) {
        return IconColorCache.getInstance().getColorForBitmap(context, getIcon(context), null);
    }

    public abstract Bitmap getIcon(Context context);
}
