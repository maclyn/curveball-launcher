package com.inipage.homelylauncher.folders;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Represents something rendered in a row with an icon and a label.
 */
public abstract class RowContent {

    public abstract String getLabel(Context c);

    public abstract Bitmap getIcon(Context context);
}
