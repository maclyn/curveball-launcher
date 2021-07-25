package com.inipage.homelylauncher.drawer;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.Comparator;
import java.util.Locale;

public abstract class FastScrollable {

    public final static char NUMERIC = '#';
    private static final Comparator<Character> CHAR_COMPARATOR = (o1c, o2c) -> o1c - o2c;
    private static final Comparator<FastScrollable> COMPARATOR = (o1, o2) -> {
        final char o1c = o1.getScrollableField();
        final char o2c = o2.getScrollableField();
        if (o1c == NUMERIC && o2c != NUMERIC) {
            return -1;
        } else if (o1c != NUMERIC && o2c == NUMERIC) {
            return 1;
        }
        final int rVal = CHAR_COMPARATOR.compare(o1c, o2c);
        if (rVal == 0) {
            return o1.getName().compareToIgnoreCase(o2.getName());
        }
        return rVal;
    };
    private char mScrollableField = 0;

    public static Comparator<Character> getCharComparator() {
        return CHAR_COMPARATOR;
    }

    public static Comparator<FastScrollable> getComparator() {
        return COMPARATOR;
    }

    public char getScrollableField() {
        if (mScrollableField != 0) {
            return mScrollableField;
        }

        @Nullable final String assignedName = getName();
        if (TextUtils.isEmpty(assignedName)) {
            return (mScrollableField = NUMERIC);
        }
        final char firstChar = assignedName.toUpperCase(Locale.US).charAt(0);
        if (Character.isDigit(firstChar)) {
            return (mScrollableField = NUMERIC);
        }
        if (Character.isAlphabetic(firstChar) || Character.isIdeographic(firstChar)) {
            return (mScrollableField = firstChar);
        }
        return (mScrollableField = NUMERIC); // Symbols, etc.
    }

    public abstract String getName();
}
