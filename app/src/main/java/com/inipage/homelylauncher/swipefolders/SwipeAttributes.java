package com.inipage.homelylauncher.swipefolders;

import android.content.Context;
import android.view.View;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.SizeDimenAttribute;
import com.inipage.homelylauncher.utils.SizeValAttribute;

import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

public class SwipeAttributes {

    public static final int MAXIMUM_ELEMENTS_PER_ROW = 9;
    public static final int MAXIMUM_ELEMENTS_PER_COLUMN = 3;

    @SizeDimenAttribute(value = R.dimen.total_dock_height)
    public int COLLAPSED_HEIGHT_DP = intValue();
    @SizeDimenAttribute(value = R.dimen.contextual_dock_height)
    public int DEAD_ZONE_DP = intValue();
    @SizeValAttribute(244)
    public int TOUCH_EXPANDED_HEIGHT_DP = intValue();
    @SizeValAttribute(8)
    public int INDICATOR_HEIGHT_DP = intValue();
    @SizeValAttribute(24)
    public int ACCORDION_IDLE_EDGE_PADDING_DP = intValue();
    /**
     * Lead in distance when backtracking
     **/
    @SizeValAttribute(32)
    public int TOUCH_BACKTRACKING_LEAD_IN_DISTANCE_DP = intValue();

    public SwipeAttributes(Context context) {
        AttributeApplier.applyDensity(this, context);
    }

    public static int getAlphaFromFloat(float modifier, float alphaPercent) {
        return Math.round(modifier * alphaPercent * 255);
    }

    public int getLineBaseline(View view) {
        return view.getHeight() - DEAD_ZONE_DP - ((COLLAPSED_HEIGHT_DP - DEAD_ZONE_DP) / 2);
    }
}
