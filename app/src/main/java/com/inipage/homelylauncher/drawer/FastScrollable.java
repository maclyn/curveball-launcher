package com.inipage.homelylauncher.drawer;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.Comparator;
import java.util.Locale;

public abstract class FastScrollable {

    private final static char NUMERIC = '#';
    private final static char UNKNOWN = '?';
    private static final Comparator<Character> mCharComparator = (o1c, o2c) -> o1c - o2c;
    private static final Comparator<FastScrollable> mComparator = (o1, o2) -> {
        final char o1c = o1.getScrollableField();
        final char o2c = o2.getScrollableField();
        final int rVal = mCharComparator.compare(o1c, o2c);
        if (rVal == 0) {
            return o1.getName().compareToIgnoreCase(o2.getName());
        }
        return rVal;
    };
    private char mScrollableField = 0;

    public static Comparator<Character> getCharComparator() {
        return mCharComparator;
    }

    public static Comparator<FastScrollable> getComparator() {
        return mComparator;
    }

    public char getScrollableField() {
        if (mScrollableField != 0) {
            return mScrollableField;
        }

        @Nullable final String assignedName = getName();
        if (TextUtils.isEmpty(assignedName)) {
            return (mScrollableField = UNKNOWN);
        }
        final char firstChar = assignedName.toUpperCase(Locale.getDefault()).charAt(0);
        if (Character.isDigit(firstChar)) {
            return (mScrollableField = NUMERIC);
        }
        if (Character.isAlphabetic(firstChar) || Character.isIdeographic(firstChar)) {
            return (mScrollableField = firstChar);
        }
        return (mScrollableField = UNKNOWN); // Symbols, etc.
    }

    public abstract String getName();
}
