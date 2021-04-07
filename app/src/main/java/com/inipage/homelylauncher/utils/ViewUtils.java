package com.inipage.homelylauncher.utils;

import android.animation.Animator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.lang.reflect.Field;

import javax.annotation.Nullable;

public class ViewUtils {

    public static View createFillerView(Context context, int newHeight) {
        View view = new View(context);
        view.setLayoutParams(
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, newHeight));
        setHeight(view, newHeight);
        return view;
    }

    public static void setHeight(View child, int newHeight) {
        ViewGroup.LayoutParams params = child.getLayoutParams();
        params.height = newHeight;
        child.setLayoutParams(params);
    }

    public static View createFillerWidthView(Context context, int newWidth) {
        View view = new View(context);
        view.setLayoutParams(
            new ViewGroup.LayoutParams(newWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        setWidth(view, newWidth);
        return view;
    }

    public static void setWidth(View child, int newWidth) {
        ViewGroup.LayoutParams params = child.getLayoutParams();
        params.width = newWidth;
        child.setLayoutParams(params);
    }

    public static boolean exceedsSlop(
        MotionEvent event, double startX, double startY, Context ctx) {
        return exceedsSlop(event, startX, startY, ctx, 1);
    }

    public static boolean exceedsSlop(
        MotionEvent event, double startX, double startY, Context ctx, double slopFactor) {
        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            return false;
        }
        final double dist = Math.hypot(event.getRawX() - startX, event.getRawY() - startY);
        return dist > (ViewConfiguration.get(ctx).getScaledTouchSlop() * slopFactor);
    }

    public static void performSyntheticMeasure(View view) {
        final Rect windowBounds = windowBounds(view.getContext());
        performSyntheticMeasure(view, windowBounds.height(), windowBounds.width());
    }

    public static Rect windowBounds(Context context) {
        @Nullable final Activity activity = activityOf(context);
        if (activity == null) {
            return new Rect();
        }
        View decorView = activity.getWindow().getDecorView();
        final int[] out = new int[2];
        decorView.getLocationOnScreen(out);
        return new Rect(
            out[0], out[1], out[0] + decorView.getWidth(), out[1] + decorView.getHeight());
    }

    public static void performSyntheticMeasure(View view, int height, int width) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
    }

    // From privately exported android.material.contextutils
    public static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static int getKeyboardOffset(View view) {
        final Rect tempRect = new Rect();
        view.getWindowVisibleDisplayFrame(tempRect);
        try {
            final Field attachInfoField = View.class.getDeclaredField("mAttachInfo");
            attachInfoField.setAccessible(true);
            @Nullable final Object attachInfo = attachInfoField.get(view);
            if (attachInfo == null) {
                return 0;
            }
            final Field visibleInsets =
                attachInfo.getClass().getDeclaredField("mVisibleInsets");
            final Field stableInsets =
                attachInfo.getClass().getDeclaredField("mStableInsets");
            visibleInsets.setAccessible(true);
            stableInsets.setAccessible(true);
            @Nullable final Rect stableInsetsValue = (Rect) stableInsets.get(attachInfo);
            @Nullable final Rect visibleInsetsValue = (Rect) visibleInsets.get(attachInfo);
            if (stableInsetsValue == null || visibleInsetsValue == null) {
                return 0;
            }
            // visibleInsetsValue accounts for navigation bar and keyboard from panning
            return visibleInsetsValue.bottom - stableInsetsValue.bottom;
        } catch (SecurityException | NoSuchFieldException | IllegalAccessException e) {
            return 0;
        }
    }

    public static Animator.AnimatorListener onEndListener(Runnable r) {
        return new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                r.run();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEnd(animation);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        };
    }
}