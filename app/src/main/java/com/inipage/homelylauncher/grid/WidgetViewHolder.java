package com.inipage.homelylauncher.grid;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.inipage.homelylauncher.model.ClassicGridItem;
import com.inipage.homelylauncher.model.GridItem;
import com.inipage.homelylauncher.widgets.WidgetLifecycleUtils;

public class WidgetViewHolder extends GridViewHolder {

    private final AppWidgetProviderInfo mAppWidgetProviderInfo;
    private final AppWidgetHostView mWidgetHostView;

    public WidgetViewHolder(
        AppWidgetHostView hostView,
        AppWidgetProviderInfo widgetProviderInfo,
        GridItem gridItem)
    {
        super(hostView.getContext(), gridItem);
        mWidgetHostView = hostView;
        mAppWidgetProviderInfo = widgetProviderInfo;
        mRootView.addView(
            mWidgetHostView,
            0,
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onResized() {
        super.onResized();
        final int width = getGridMetrics().getWidthOfColumnSpanPx(mItem.getWidth());
        final int height = getGridMetrics().getHeightOfRowSpanPx(mItem.getHeight());
        WidgetLifecycleUtils.updateAppWidgetSize(mWidgetHostView, width, height);
    }

    @Override
    public View getDragView() {
        return mWidgetHostView;
    }

    public AppWidgetProviderInfo getProviderInfo() {
        return mAppWidgetProviderInfo;
    }
}
