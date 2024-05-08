package com.inipage.homelylauncher.views;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.caches.AppLabelCache;
import com.inipage.homelylauncher.caches.ShortcutWrapper;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.utils.InstalledAppUtils;
import com.inipage.homelylauncher.utils.ViewUtils;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import static android.os.Process.myUserHandle;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class AppPopupMenu {

    @Nullable
    private String mDecorViewHandle;

    public void show(
        int x,
        int y,
        boolean useRemoveAction,
        final Context context,
        final ApplicationIcon target,
        final Listener listener) {

        final LayoutInflater inflater = LayoutInflater.from(context);
        final Rect bounds = ViewUtils.windowBounds(context);
        final LauncherApps launcherApps =
            (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        final int density = context.getResources().getConfiguration().densityDpi;
        final boolean canBeUninstalled =
            InstalledAppUtils.canUninstallPackage(context, target.getPackageName());
        List<ShortcutWrapper> shortcuts =
            AppInfoCache.get().getPackageShortcuts(target.getPackageName());
        shortcuts = shortcuts
            .stream()
            .sorted((o1, o2) -> {
                int d1 = o1.getShortcutInfo().isDynamic() ? 1 : 0;
                int d2 = o2.getShortcutInfo().isDynamic() ? 1 : 0;
                return d1 - d2;
            }).collect(Collectors.toList());
        if (shortcuts.size() > 5) {
            shortcuts = shortcuts.subList(0, 4);
        }
        final View appPopupMenu = inflater.inflate(R.layout.app_popup_menu, null);
        final TextView appPopupRootTitle =
            (TextView) inflater.inflate(R.layout.app_popup_root_title, null);
        final View appPopupButtons = inflater.inflate(R.layout.app_popup_root_actions, null);
        final LinearLayout shortcutContainer =
            appPopupMenu.findViewById(R.id.app_popup_item_container);

        // Setup the view data
        final ImageView uninstallAppButton =
            appPopupButtons.findViewById(R.id.uninstall_app_button);
        final ImageView hideAppButton =
            appPopupButtons.findViewById(R.id.hide_app_button);
        final ImageView removeAppButton =
            appPopupButtons.findViewById(R.id.remove_app_button);
        final ImageView appInfoButton =
            appPopupButtons.findViewById(R.id.info_app_button);
        final View uninstallWhitespace = appPopupButtons.findViewById(R.id.uninstall_whitespace);
        final View hideWhitespace = appPopupButtons.findViewById(R.id.hide_whitespace);
        final View removeWhitespace = appPopupButtons.findViewById(R.id.remove_whitespace);
        uninstallAppButton.setVisibility(canBeUninstalled ? VISIBLE : GONE);
        uninstallWhitespace.setVisibility(canBeUninstalled ? VISIBLE : GONE);
        hideAppButton.setVisibility(useRemoveAction ? GONE : VISIBLE);
        hideWhitespace.setVisibility(useRemoveAction ? GONE : VISIBLE);
        removeAppButton.setVisibility(useRemoveAction ? VISIBLE : GONE);
        removeWhitespace.setVisibility(useRemoveAction ? VISIBLE : GONE);
        hideAppButton.setOnClickListener(
            v -> {
                DecorViewManager.get(context).removeView(mDecorViewHandle);
                listener.onRemove();
            });
        removeAppButton.setOnClickListener(
            v -> {
                DecorViewManager.get(context).removeView(mDecorViewHandle);
                listener.onRemove();
            });
        uninstallAppButton.setOnClickListener(
            v -> {
                DecorViewManager.get(context).removeView(mDecorViewHandle);
                InstalledAppUtils.launchUninstallPackageIntent(
                    v.getContext(),
                    target.getPackageName());
            });
        appInfoButton.setOnClickListener(
            v -> {
                DecorViewManager.get(context).removeView(mDecorViewHandle);
                InstalledAppUtils.launchPackageInfoIntent(v.getContext(), target.getPackageName());
            });
        appPopupRootTitle.setText(AppLabelCache.getInstance(context).getLabel(target));
        for (ShortcutWrapper shortcut : shortcuts) {
            addItemRow(
                context,
                shortcutContainer,
                shortcut.getLabel(),
                launcherApps.getShortcutIconDrawable(shortcut.getShortcutInfo(), density),
                v -> launcherApps.startShortcut(
                    shortcut.getShortcutInfo().getPackage(),
                    shortcut.getShortcutInfo().getId(),
                    new Rect(
                        v.getLeft(),
                        v.getTop(),
                        v.getLeft() + v.getWidth(),
                        v.getTop() + v.getHeight()),
                    null, myUserHandle())
            );
        }

        // Edge case: no shortcuts; hide the app icons and map to shortcut-like view
        final boolean presentingAppToolsAsItems = shortcuts.isEmpty();
        if (presentingAppToolsAsItems) {
            if (hideAppButton.getVisibility() == VISIBLE) {
                hideAppButton.setVisibility(GONE);
                addItemRow(
                    context,
                    shortcutContainer,
                    context.getString(R.string.hide_app),
                    hideAppButton.getDrawable(),
                    v -> listener.onRemove());
            }
            if (removeAppButton.getVisibility() == VISIBLE) {
                removeAppButton.setVisibility(GONE);
                addItemRow(
                    context,
                    shortcutContainer,
                    context.getString(R.string.remove_shortcut),
                    removeAppButton.getDrawable(),
                    v -> listener.onRemove());
            }
            if (uninstallAppButton.getVisibility() == VISIBLE) {
                uninstallAppButton.setVisibility(GONE);
                addItemRow(
                    context,
                    shortcutContainer,
                    context.getString(R.string.uninstall),
                    uninstallAppButton.getDrawable(),
                    v ->
                        InstalledAppUtils.launchUninstallPackageIntent(
                            v.getContext(), target.getPackageName()));
            }
            addItemRow(
                context,
                shortcutContainer,
                context.getString(R.string.app_info),
                appInfoButton.getDrawable(),
                v ->
                    InstalledAppUtils.launchPackageInfoIntent(
                        v.getContext(), target.getPackageName()));
            appInfoButton.setVisibility(GONE);
        }

        // Choose the correct anchor point, setup the view corners to match, and assemble the
        // views
        final int expectedWidth =
            (int) context.getResources().getDimension(R.dimen.app_popup_menu_width);
        final int expectedHeight =
            (int)
                (
                    context.getResources().getDimension(R.dimen.bar_height) *
                        ((presentingAppToolsAsItems ? 1.5 : 2) + shortcuts.size()));
        final Anchor anchor =
            Anchor.chooseAnchor(context, x, y, expectedWidth, expectedHeight);
        ((ImageView) appPopupMenu.findViewById(R.id.top_bar_left))
            .setImageResource(anchor.getTlRID());
        ((ImageView) appPopupMenu.findViewById(R.id.top_bar_right))
            .setImageResource(anchor.getTrRID());
        ((ImageView) appPopupMenu.findViewById(R.id.bottom_bar_left))
            .setImageResource(anchor.getBlID());
        ((ImageView) appPopupMenu.findViewById(R.id.bottom_bar_right))
            .setImageResource(anchor.getBrID());
        if (presentingAppToolsAsItems) {
            final int halfHeight =
                (int) context.getResources().getDimension(R.dimen.bar_height_half);
            if (Anchor.isTopAnchor(anchor)) {
                ViewUtils.setHeight(
                    appPopupMenu.findViewById(R.id.app_popup_top_bar_container), halfHeight);
            } else {
                ViewUtils.setHeight(
                    appPopupMenu.findViewById(R.id.app_popup_bottom_bar_container), halfHeight);
            }
        }

        final boolean isBottomAnchor = Anchor.isBottomAnchor(anchor);
        ((FrameLayout) appPopupMenu.findViewById(R.id.top_bar_item_container))
            .addView(isBottomAnchor ? appPopupRootTitle : appPopupButtons);
        ((FrameLayout) appPopupMenu.findViewById(R.id.bottom_bar_item_container))
            .addView(isBottomAnchor ? appPopupButtons : appPopupRootTitle);
        appPopupMenu.measure(
            View.MeasureSpec.makeMeasureSpec(expectedWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(bounds.height(), View.MeasureSpec.AT_MOST));

        final int xAnchorPoint =
            Anchor.isRightAnchor(anchor) ?
            x - appPopupMenu.getMeasuredWidth() :
            (
                Anchor.isCenterAnchor(anchor) ?
                x - (appPopupMenu.getMeasuredWidth() / 2) :
                x);
        final int yAnchorPoint = y - (isBottomAnchor ? appPopupMenu.getMeasuredHeight() : 0);
        mDecorViewHandle = DecorViewManager.get(context).attachView(
            appPopupMenu,
            new DecorViewManager.Callback() {
                @Override
                public Animator provideExitAnimation(View view) {
                    final AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.setInterpolator(new AccelerateInterpolator());
                    animatorSet.playTogether(
                        ObjectAnimator.ofFloat(view, "scaleX", 1, 0),
                        ObjectAnimator.ofFloat(view, "scaleY", 1, 0),
                        ObjectAnimator.ofFloat(view, "alpha", 1, 0));
                    return animatorSet;
                }

                @Override
                public void onDismissed(View removedView, boolean byBackgroundTap) {
                    listener.onDismiss();
                }
            },
            (int) context.getResources().getDimension(R.dimen.app_popup_menu_width),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            xAnchorPoint,
            yAnchorPoint);

        // pivotX and pivotY need to be set correctly for the scale animation to work right
        if (Anchor.isBottomAnchor(anchor)) {
            appPopupMenu.setPivotY(appPopupMenu.getMeasuredHeight());
        } else {
            appPopupMenu.setPivotY(0);
        }
        if (Anchor.isRightAnchor(anchor)) {
            appPopupMenu.setPivotX(appPopupMenu.getMeasuredWidth());
        } else if (Anchor.isCenterAnchor(anchor)) {
            appPopupMenu.setPivotX(appPopupMenu.getMeasuredWidth() / 2F);
        } else {
            appPopupMenu.setPivotX(0);
        }

        final Animator revealAnimation =
            ViewAnimationUtils.createCircularReveal(
                appPopupMenu,
                Anchor.isRightAnchor(anchor) ?
                appPopupMenu.getMeasuredWidth() :
                (int) (
                    Anchor.isCenterAnchor(anchor) ?
                    x - (appPopupMenu.getMeasuredWidth() / 2F) :
                    0),
                isBottomAnchor ? appPopupMenu.getMeasuredHeight() : 0,
                0,
                (float)
                    Math.hypot(appPopupMenu.getMeasuredHeight(), appPopupMenu.getMeasuredWidth()));

        final ObjectAnimator translationAnimation =
            ObjectAnimator.ofFloat(
                appPopupMenu,
                "translationY",
                isBottomAnchor ?
                new float[]{appPopupMenu.getMeasuredHeight() / 10F, 0} :
                new float[]{-appPopupMenu.getMeasuredHeight() / 10F, 0});

        final ObjectAnimator alphaAnimation =
            ObjectAnimator.ofFloat(appPopupMenu, "alpha", 0, 1);
        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setTarget(appPopupMenu);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.setDuration(250L);
        animatorSet.playTogether(revealAnimation, alphaAnimation, translationAnimation);
        animatorSet.start();
    }

    private void addItemRow(
        Context context,
        LinearLayout container,
        String label,
        Drawable icon,
        View.OnClickListener action) {
        final View itemView =
            LayoutInflater.from(context).inflate(R.layout.app_popup_root_shortcut, null);
        ((TextView) itemView.findViewById(R.id.app_popup_root_shortcut_label))
            .setText(label);
        ((ImageView) itemView.findViewById(R.id.app_popup_root_shortcut_icon))
            .setImageDrawable(icon);
        itemView.setOnClickListener(v -> {
            DecorViewManager.get(context).removeView(mDecorViewHandle);
            action.onClick(v);
        });
        container.addView(
            itemView,
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    public enum Anchor {
        TOP_RIGHT(
            R.drawable.rounded_corner_top_right_less,
            R.drawable.rounded_corner_top_left,
            R.drawable.rounded_corner_bottom_right,
            R.drawable.rounded_corner_bottom_left,
            Gravity.CENTER),
        TOP_LEFT(
            R.drawable.rounded_corner_top_right,
            R.drawable.rounded_corner_top_left_less,
            R.drawable.rounded_corner_bottom_right,
            R.drawable.rounded_corner_bottom_left,
            Gravity.CENTER),
        TOP_CENTER(
            R.drawable.rounded_corner_top_right,
            R.drawable.rounded_corner_top_left,
            R.drawable.rounded_corner_bottom_right,
            R.drawable.rounded_corner_bottom_left,
            Gravity.CENTER),
        BOTTOM_RIGHT(
            R.drawable.rounded_corner_top_right,
            R.drawable.rounded_corner_top_left,
            R.drawable.rounded_corner_bottom_right_less,
            R.drawable.rounded_corner_bottom_left,
            Gravity.CENTER),
        BOTTOM_LEFT(
            R.drawable.rounded_corner_top_right,
            R.drawable.rounded_corner_top_left,
            R.drawable.rounded_corner_bottom_right,
            R.drawable.rounded_corner_bottom_left_less,
            Gravity.CENTER),
        BOTTOM_CENTER(
            R.drawable.rounded_corner_top_right,
            R.drawable.rounded_corner_top_left,
            R.drawable.rounded_corner_bottom_right,
            R.drawable.rounded_corner_bottom_left,
            Gravity.CENTER);

        final int mTrRID, mTlRID, mBrID, mBlID, mGravity;

        Anchor(int trRID, int tlRID, int brRID, int blRID, int gravity) {
            mTrRID = trRID;
            mTlRID = tlRID;
            mBrID = brRID;
            mBlID = blRID;
            mGravity = gravity;
        }

        public static Anchor chooseAnchor(
            Context context,
            int touchX,
            int touchY,
            int viewWidth,
            int viewHeight) {
            final Rect bounds = ViewUtils.windowBounds(context);
            final boolean hasLeftAffinity = touchX < (bounds.right - (bounds.width() / 2));
            final boolean hasCenterAffinity =
                touchX > (bounds.left + (bounds.width() * 0.45)) &&
                    touchX < (bounds.left + (bounds.width() * 0.55));
            final boolean spillsOverLeft = touchX - viewWidth < bounds.left;
            final boolean spillsOverRight = touchX + viewWidth > bounds.right;
            final boolean spillsOverTop = touchY - viewHeight < bounds.top;
            if (hasCenterAffinity) {
                return spillsOverTop ? TOP_CENTER : BOTTOM_CENTER;
            } else if (hasLeftAffinity) {
                return spillsOverTop ?
                       (spillsOverRight ? TOP_CENTER : TOP_LEFT) :
                       (spillsOverRight ? BOTTOM_CENTER : BOTTOM_LEFT);
            } else { // Right affinity
                return spillsOverTop ?
                       (spillsOverLeft ? TOP_CENTER : TOP_RIGHT) :
                       (spillsOverLeft ? BOTTOM_CENTER : BOTTOM_RIGHT);
            }
        }

        public static boolean isCenterAnchor(Anchor anchor) {
            return anchor == BOTTOM_CENTER || anchor == TOP_CENTER;
        }

        public static boolean isBottomAnchor(Anchor anchor) {
            return anchor == BOTTOM_CENTER || anchor == BOTTOM_LEFT || anchor == BOTTOM_RIGHT;
        }

        public static boolean isRightAnchor(Anchor anchor) {
            return anchor == BOTTOM_RIGHT || anchor == TOP_RIGHT;
        }

        public static boolean isTopAnchor(Anchor anchor) {
            return anchor == TOP_CENTER || anchor == TOP_LEFT || anchor == TOP_RIGHT;
        }

        public int getTrRID() {
            return mTrRID;
        }

        public int getTlRID() {
            return mTlRID;
        }

        public int getBrID() {
            return mBrID;
        }

        public int getBlID() {
            return mBlID;
        }

        public int getGravity() {
            return mGravity;
        }
    }

    public interface Listener {
        void onRemove();

        void onDismiss();
    }
}
