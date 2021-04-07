package com.inipage.homelylauncher.grid;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.model.GridItem;

public class MissingViewHolder extends GridViewHolder {

    private final TextView mDeadView;

    public MissingViewHolder(Context context, GridItem item) {
        super(context, item);
        mDeadView = new TextView(context);
        mDeadView.setText(R.string.installed_widget_app_missing);
        mDeadView.setGravity(Gravity.CENTER);
        mDeadView.setBackgroundColor(context.getColor(R.color.warning_red));
        mRootView.addView(
            mDeadView,
            0,
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected View getDragView() {
        return mDeadView;
    }
}
