package com.inipage.homelylauncher.views;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.ViewUtils;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class BottomSheetHelper {

    private final List<BuilderItem> mItems;
    private final List<BuilderItem> mActionItems;
    @Nullable
    private View mContentView;
    private boolean mFixedToScreenHeightPercent;
    private float mScreenHeightPercent;

    public BottomSheetHelper() {
        mItems = new ArrayList<>();
        mActionItems = new ArrayList<>();
        mContentView = null;
    }

    public BottomSheetHelper addItem(int iconResId, int stringResId, Runnable callback) {
        mItems.add(new BuilderItem(iconResId, stringResId, callback));
        return this;
    }

    public BottomSheetHelper addActionItem(int stringResId, Runnable callback) {
        mActionItems.add(new BuilderItem(-1, stringResId, callback));
        return this;
    }

    public BottomSheetHelper setContentView(View view) {
        mContentView = view;
        return this;
    }

    public BottomSheetHelper setFixedScreenPercent(float percent) {
        mFixedToScreenHeightPercent = true;
        mScreenHeightPercent = percent;
        return this;
    }

    public String show(Context context, String title) {
        final LayoutInflater inflater = LayoutInflater.from(context);
        final BottomSheetContainer rootView =
            (BottomSheetContainer) inflater.inflate(R.layout.bottom_sheet_container, null);
        @Nullable final Activity activity = ViewUtils.activityOf(context);
        ((TextView) rootView.findViewById(R.id.bottom_sheet_title)).setText(title);

        rootView.setLayoutParams(
            new FrameLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        int scrimHeight = 0;
        if (activity instanceof ProvidesOverallDimensions) {
            scrimHeight = ((ProvidesOverallDimensions) activity).provideScrims().second;
        }
        if (mFixedToScreenHeightPercent && activity instanceof ProvidesOverallDimensions) {
            ProvidesOverallDimensions dimensionProvider = (ProvidesOverallDimensions) activity;
            final int contentViewHeight =
                (int) (dimensionProvider.provideOverallBounds().height() * mScreenHeightPercent);
            ViewUtils.setHeight(rootView, contentViewHeight + scrimHeight);
            ViewUtils.setHeight(
                rootView.findViewById(R.id.bottom_sheet_container),
                contentViewHeight);
        }
        ViewUtils.setHeight(rootView.findViewById(R.id.bottom_sheet_bottom_scrim), scrimHeight);
        final String handle = DecorViewManager.get(context).attachView(
            rootView,
            new DecorViewManager.Callback() {
                @Override
                public boolean shouldTintBackgroundView() {
                    return true;
                }

                @Nullable
                @Override
                public Animator provideExitAnimation(View view) {
                    return ObjectAnimator.ofFloat(view, "translationY", view.getHeight());
                }
            },
            (FrameLayout.LayoutParams) rootView.getLayoutParams());
        rootView.attachDecorView(handle);

        final LinearLayout containerView = rootView.findViewById(R.id.bottom_sheet_container);
        for (BuilderItem item : mItems) {
            final View menuItemView =
                inflater.inflate(R.layout.bottom_sheet_menu_item, rootView, false);
            ((ImageView) menuItemView.findViewById(R.id.bottom_sheet_menu_item_icon))
                .setImageResource(item.getIconResId());
            ((TextView) menuItemView.findViewById(R.id.bottom_sheet_menu_item_text))
                .setText(item.getStringResId());
            menuItemView.setOnClickListener(v -> {
                DecorViewManager.get(context).removeView(handle);
                item.getCallback().run();
            });
            containerView.addView(
                menuItemView, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        if (mContentView != null) {
            containerView.addView(
                mContentView,
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        final LinearLayout bottomSheetContainerView =
            rootView.findViewById(R.id.bottom_sheet_action_container);
        for (BuilderItem item : mActionItems) {
            final View menuItemView =
                inflater.inflate(R.layout.bottom_sheet_action_item, rootView, false);
            ((TextView) menuItemView.findViewById(R.id.bottom_sheet_action_item_text))
                .setText(item.getStringResId());
            menuItemView.setOnClickListener(v -> {
                DecorViewManager.get(context).removeView(handle);
                item.getCallback().run();
            });
            bottomSheetContainerView.addView(
                menuItemView, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        }
        rootView.setClipToOutline(true);
        rootView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                final int radius =
                    (int) view.getResources()
                        .getDimension(R.dimen.bottom_sheet_corner_round_radius);
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + radius, radius);
            }
        });

        ViewUtils.performSyntheticMeasure(rootView);
        rootView.setTranslationY(
            mFixedToScreenHeightPercent ?
            rootView.getLayoutParams().height :
            rootView.getMeasuredHeight());
        rootView
            .animate()
            .translationY(0)
            .setDuration(DecorViewManager.TINT_ANIMATION_DURATION)
            .setInterpolator(DecorViewManager.TINT_INTERPOLATOR)
            .start();
        return handle;
    }

    private static class BuilderItem {

        final int mIconResId;
        final int mStringResId;
        final Runnable mCallback;

        private BuilderItem(int iconResId, int stringResId, Runnable callback) {
            mIconResId = iconResId;
            mStringResId = stringResId;
            mCallback = callback;
        }

        public int getIconResId() {
            return mIconResId;
        }

        public int getStringResId() {
            return mStringResId;
        }

        public Runnable getCallback() {
            return mCallback;
        }
    }
}
