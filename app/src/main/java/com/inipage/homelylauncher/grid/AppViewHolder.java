package com.inipage.homelylauncher.grid;

import android.content.Context;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.AppLabelCache;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.drawer.BitmapView;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.ClassicGridItem;
import com.inipage.homelylauncher.utils.InstalledAppUtils;

public class AppViewHolder extends GridViewHolder {

    private final BitmapView mBitmapView;
    private final TextView mLabelView;

    public AppViewHolder(Context context, ClassicGridItem gridItem) {
        super(context, gridItem);

        final LayoutInflater inflater = LayoutInflater.from(context);
        final View gridIcon = inflater.inflate(R.layout.grid_icon, null);
        mBitmapView = gridIcon.findViewById(R.id.grid_icon_image);
        mLabelView = gridIcon.findViewById(R.id.grid_icon_label);
        mBitmapView.setBitmap(
            IconCacheSync
                .getInstance(context)
                .getActivityIcon(
                    gridItem.getPackageName(),
                    gridItem.getActivityName()));
        mLabelView.setText(AppLabelCache.getInstance(context).getLabel(
            gridItem.getPackageName(), gridItem.getActivityName()));
        gridIcon.setOnClickListener(v ->
            InstalledAppUtils.launchApp(v, gridItem.getPackageName(), gridItem.getActivityName()));
        mRootView.addView(
            gridIcon,
            0,
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public ApplicationIcon getAppIcon() {
        return new ApplicationIcon(
            getItem().getPackageName(),
            getItem().getActivityName(),
            AppLabelCache.getInstance(getRootContainer().getContext()).getLabel(
                new Pair<>(getItem().getPackageName(), getItem().getActivityName()))
        );
    }

    @Override
    public View getDragView() {
        return mBitmapView;
    }
}