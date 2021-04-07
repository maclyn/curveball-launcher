package com.inipage.homelylauncher.views;

import android.animation.Animator;
import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BaseInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.ViewUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 * Helper for managing views added directly to the DecorView of an Activity's Window.
 */
public class DecorViewManager {

    public static final long TINT_ANIMATION_DURATION = 200L;
    public static final BaseInterpolator TINT_INTERPOLATOR = new AccelerateDecelerateInterpolator();
    private static final WeakHashMap<Activity, DecorViewManager> s_INSTANCE_MAP =
        new WeakHashMap<>();
    private final WeakReference<Activity> mActivityRef;
    private final Map<String, View> mViewKeyToView = new HashMap<>();
    private final Map<String, Callback> mViewKeyToCallback = new HashMap<>();
    private final Map<String, View> mViewKeyToBackground = new HashMap<>();
    private final List<String> mViewKeyStack = new ArrayList<>();

    private DecorViewManager(Activity activity) {
        mActivityRef = new WeakReference<>(activity);
    }

    public static DecorViewManager get(Context context) {
        return get(ViewUtils.activityOf(context));
    }

    public static DecorViewManager get(Activity activity) {
        if (!s_INSTANCE_MAP.containsKey(activity)) {
            s_INSTANCE_MAP.put(activity, new DecorViewManager(activity));
        }
        return s_INSTANCE_MAP.get(activity);
    }

    public String attachView(
        View view,
        Callback callback,
        int width,
        int height,
        int x,
        int y) {
        final FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);
        return attachViewImpl(view, layoutParams, callback);
    }

    private String attachViewImpl(
        View view,
        FrameLayout.LayoutParams params,
        Callback listener) {
        final String key = UUID.randomUUID().toString();
        attachBackgroundView(key, listener.shouldTintBackgroundView());
        @Nullable FrameLayout decorView = getDecorView();
        if (decorView != null) {
            decorView.addView(view, params);
        }
        mViewKeyToView.put(key, view);
        mViewKeyToCallback.put(key, listener);
        mViewKeyStack.add(key);
        return key;
    }

    private void attachBackgroundView(String key, boolean shouldTintView) {
        @Nullable FrameLayout decorView = getDecorView();
        @Nullable Activity activity = mActivityRef.get();
        if (decorView == null || activity == null) {
            return;
        }

        View fullscreenTransparentView = new View(activity);
        fullscreenTransparentView.setClickable(true);
        fullscreenTransparentView.setOnClickListener(v -> removeView(key));
        decorView.addView(
            fullscreenTransparentView,
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (shouldTintView) {
            fullscreenTransparentView.setBackgroundColor(
                activity.getColor(R.color.decor_background_tint_color));
            fullscreenTransparentView.setAlpha(0F);
            fullscreenTransparentView
                .animate()
                .alpha(1F)
                .setInterpolator(TINT_INTERPOLATOR)
                .setDuration(TINT_ANIMATION_DURATION)
                .start();
        }
        mViewKeyToBackground.put(key, fullscreenTransparentView);
    }

    @Nullable
    private FrameLayout getDecorView() {
        if (mActivityRef.get() == null) {
            return null;
        }
        View decorView = mActivityRef.get().getWindow().getDecorView();
        if (!(decorView instanceof FrameLayout)) {
            return null;
        }
        return (FrameLayout) decorView;
    }

    public void removeView(String key) {
        removeViewImpl(key);
    }

    private void removeViewImpl(String key) {
        @Nullable FrameLayout decorView = getDecorView();
        @Nullable View attachedView = mViewKeyToView.get(key);
        @Nullable View backgroundView = mViewKeyToBackground.get(key);
        Callback callback = Objects.requireNonNull(mViewKeyToCallback.get(key));
        callback.onDismissedByBackgroundTap(attachedView);
        if (decorView != null && attachedView != null) {
            @Nullable final Animator exitAnimator = callback.provideExitAnimation(attachedView);
            if (exitAnimator != null) {
                exitAnimator.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        decorView.removeView(attachedView);
                        decorView.removeView(backgroundView);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        onAnimationEnd(animation);
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                    }
                });
                exitAnimator.setTarget(attachedView);
                exitAnimator.setDuration(TINT_ANIMATION_DURATION);
                exitAnimator.setInterpolator(TINT_INTERPOLATOR);
                exitAnimator.start();
                detachBackgroundView(key, callback.shouldTintBackgroundView());
            } else {
                decorView.removeView(backgroundView);
                decorView.removeView(attachedView);
            }
        }
        mViewKeyToView.remove(key);
        mViewKeyToCallback.remove(key);
        mViewKeyStack.remove(key);
    }

    private void detachBackgroundView(String key, boolean wasTinted) {
        @Nullable final View backgroundView = mViewKeyToBackground.remove(key);
        if (backgroundView == null) {
            return;
        }

        @Nullable FrameLayout decorView = getDecorView();
        if (decorView == null) {
            return;
        }
        if (!wasTinted) {
            decorView.removeView(backgroundView);
            return;
        }
        backgroundView
            .animate()
            .alpha(0F)
            .setDuration(TINT_ANIMATION_DURATION)
            .setInterpolator(TINT_INTERPOLATOR)
            .setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    decorView.removeView(backgroundView);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    onAnimationEnd(animation);
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
    }

    public String attachView(
        View view,
        Callback callback,
        FrameLayout.LayoutParams layoutParams) {
        return attachViewImpl(view, layoutParams, callback);
    }

    public void updateViewPosition(String key, int x, int y) {
        @Nullable FrameLayout decorView = getDecorView();
        @Nullable View attachedView = mViewKeyToView.get(key);
        if (decorView != null && attachedView != null) {
            final FrameLayout.LayoutParams dragViewParams =
                (FrameLayout.LayoutParams) attachedView.getLayoutParams();
            dragViewParams.setMargins(x, y, 0, 0);
            attachedView.setLayoutParams(dragViewParams);
        }
    }

    public boolean detachAllViews() {
        if (mViewKeyToView.isEmpty()) {
            return false;
        }
        List<String> keyCopy = mViewKeyToView.keySet().stream().collect(Collectors.toList());
        for (String key : keyCopy) {
            final Callback listener = mViewKeyToCallback.get(key);
            listener.onDismissedByBackgroundTap(mViewKeyToView.get(key));
            removeViewImpl(key);
        }
        return true;
    }

    public boolean detachTopView() {
        if (mViewKeyStack.isEmpty()) {
            return false;
        }
        removeView(mViewKeyStack.get(mViewKeyStack.size() - 1));
        return true;
    }

    @Nullable
    public View getView(String key) {
        return mViewKeyToView.get(key);
    }

    public void updateTintPercent(String key, float percent) {
        if (mViewKeyToBackground.get(key) != null) {
            Objects.requireNonNull(mViewKeyToBackground.get(key)).setAlpha(percent);
        }
    }

    public interface Callback {

        /**
         * @return Whether we should tint the background view. Tinted background views get automatic
         * exit animations if there's a provided exit animation.
         */
        default boolean shouldTintBackgroundView() {
            return false;
        }

        /**
         * @param view The view to animate away.
         * @return Whether the hosting component will provide an exit animation for the view. If the
         * hosting component returns a non-null animator, we wait for that animation's cancel or
         * complete to remove the view, and also, if the background is tinted, handle that animation
         * ourselves.
         */
        @Nullable
        default Animator provideExitAnimation(View view) {
            return null;
        }

        /**
         * This view is going to be removed.
         *
         * @param removedView The View that will be removed.
         */
        default void onDismissedByBackgroundTap(View removedView) {
        }
    }
}
