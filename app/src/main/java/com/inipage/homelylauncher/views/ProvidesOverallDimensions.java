package com.inipage.homelylauncher.views;

import android.graphics.Rect;
import android.util.Pair;

public interface ProvidesOverallDimensions {

    Pair<Integer, Integer> provideScrims();

    Rect provideOverallBounds();
}
