package com.inipage.homelylauncher.views;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.ViewUtils;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class BottomSheetHelper {

    public interface OnDismissedCallback {
        void onDismissed(boolean bySwipeOrBackgroundTap);
    }

    private final List<BuilderItem> mItems;
    private final List<BuilderItem> mActionItems;

    @Nullable
    private View mContentView;
    @Nullable
    private OnDismissedCallback mOnDismissedCallback;
    private boolean mFixedToScreenHeightPercent;
    private float mScreenHeightPercent;
    private boolean mDismissedBySwipeOrBackgroundTap;

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

    public BottomSheetHelper setIsFixedHeight() {
        mFixedToScreenHeightPercent = true;
        mScreenHeightPercent = 0.85F;
        return this;
    }

    public BottomSheetHelper setOnDismissedCallback(OnDismissedCallback callback) {
        mOnDismissedCallback = callback;
        return this;
    }

    public String show(Context context, String title) {
        final LayoutInflater inflater = LayoutInflater.from(context);
        final DraggableLayout rootView =
            (DraggableLayout) inflater.inflate(R.layout.bottom_sheet_container, null);
        final LinearLayout containerView = rootView.findViewById(R.id.bottom_sheet_container);
        final LinearLayout actionContainerView =
            rootView.findViewById(R.id.bottom_sheet_action_container);
        ((TextView) rootView.findViewById(R.id.bottom_sheet_title)).setText(title);

        @Nullable final Activity activity = ViewUtils.requireActivityOf(context);
        rootView.setLayoutParams(
            new FrameLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        int bottomScrimHeight = 0;
        int topScrimHeight = 0;
        int windowHeight = 0;
        if (activity instanceof ProvidesOverallDimensions) {
            topScrimHeight = ((ProvidesOverallDimensions) activity).provideScrims().first;
            bottomScrimHeight = ((ProvidesOverallDimensions) activity).provideScrims().second;
            windowHeight = ((ProvidesOverallDimensions) activity).provideOverallBounds().height();
        }
        ViewUtils.setHeight(rootView.findViewById(R.id.bottom_sheet_bottom_scrim), bottomScrimHeight);
        final String decorHandle = DecorViewManager.get(context).attachView(
            rootView,
            new DecorViewManager.Callback() {
                @Override
                public boolean shouldTintBackgroundView() {
                    return true;
                }

                @Override
                public Animator provideExitAnimation(View view) {
                    Animator animator = ObjectAnimator.ofFloat(view, "translationY", view.getHeight());
                    animator.addListener(
                        ViewUtils.onEndListener(() -> rootView.markViewBeingAnimated(false)));
                    rootView.markViewBeingAnimated(true);
                    return animator;
                }

                @Override
                public void onDismissed(View removedView, boolean byBackgroundTap) {
                    if (byBackgroundTap) {
                        mDismissedBySwipeOrBackgroundTap = true;
                    }
                    if (mOnDismissedCallback != null) {
                        mOnDismissedCallback.onDismissed(mDismissedBySwipeOrBackgroundTap);
                    }
                }
            },
            (FrameLayout.LayoutParams) rootView.getLayoutParams());
        rootView.attachHost(new BottomSheetContainerLayoutHost(
            context,
            decorHandle,
            () -> mDismissedBySwipeOrBackgroundTap = true));
        // Attach menu items
        if (!mItems.isEmpty()) {
            ScrollView scroller = new ScrollView(context);
            LinearLayout menuItemContainer = new LinearLayout(context);
            menuItemContainer.setOrientation(LinearLayout.VERTICAL);
            scroller.addView(menuItemContainer, new ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            for (BuilderItem item : mItems) {
                final View menuItemView =
                    inflater.inflate(R.layout.bottom_sheet_menu_item, rootView, false);
                ((ImageView) menuItemView.findViewById(R.id.bottom_sheet_menu_item_icon))
                    .setImageResource(item.getIconResId());
                ((TextView) menuItemView.findViewById(R.id.bottom_sheet_menu_item_text))
                    .setText(item.getStringResId());
                menuItemView.setOnClickListener(v -> {
                    DecorViewManager.get(context).removeView(decorHandle);
                    item.getCallback().run();
                });
                menuItemContainer.addView(
                    menuItemView, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            }
            containerView.addView(
                scroller, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        // Attach a general view
        if (mContentView != null) {
            containerView.addView(
                mContentView,
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        // Attach action items
        for (BuilderItem item : mActionItems) {
            final View menuItemView =
                inflater.inflate(R.layout.bottom_sheet_action_item, rootView, false);
            ((TextView) menuItemView.findViewById(R.id.bottom_sheet_action_item_text))
                .setText(item.getStringResId());
            menuItemView.setOnClickListener(v -> {
                DecorViewManager.get(context).removeView(decorHandle);
                item.getCallback().run();
            });
            actionContainerView.addView(
                menuItemView, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        }
        // Round the view
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
        // Figure out the correct height of the overall view
        int topScrimPadding =
            context.getResources().getDimensionPixelSize(R.dimen.bottom_sheet_top_scrim_padding);
        int maxAllowedBottomSheetHeight = windowHeight - topScrimHeight - topScrimPadding;
        ViewUtils.performSyntheticMeasure(rootView);
        if (mFixedToScreenHeightPercent) {
            // Screen height percent is relative to space between scrims
            int screenSpaceWithoutScrims = windowHeight - topScrimHeight - bottomScrimHeight;
            int desiredHeight = (int) (screenSpaceWithoutScrims * mScreenHeightPercent);
            ViewUtils.setHeight(rootView, desiredHeight + bottomScrimHeight);
        } else {
            if (rootView.getMeasuredHeight() > maxAllowedBottomSheetHeight) {
                ViewUtils.setHeight(rootView, maxAllowedBottomSheetHeight);
            }
        }

        int maxContentHeight = windowHeight - topScrimPadding - bottomScrimHeight;
        rootView.setTranslationY(
            mFixedToScreenHeightPercent ?
                Math.min(rootView.getMeasuredHeight(), maxContentHeight) :
                rootView.getMeasuredHeight());
        rootView.markViewBeingAnimated(true);
        rootView
            .animate()
            .translationY(0)
            .setDuration(DecorViewManager.TINT_ANIMATION_DURATION)
            .setInterpolator(DecorViewManager.TINT_INTERPOLATOR)
            .setListener(ViewUtils.onEndListener(() -> rootView.markViewBeingAnimated(false)))
            .start();
        return decorHandle;
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
