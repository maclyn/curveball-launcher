package com.inipage.homelylauncher.pocket;

import android.content.Context;
import android.graphics.Bitmap;

import com.inipage.homelylauncher.caches.IconColorCache;

/**
 * Represents something rendered in a row with an icon and a label.
 */
public abstract class RowContent {

    public abstract String getLabel(Context c);

    public abstract Bitmap getIcon(Context context);
}
