package com.inipage.homelylauncher;

import static android.view.DragEvent.ACTION_DRAG_ENDED;
import static android.view.DragEvent.ACTION_DRAG_ENTERED;
import static android.view.DragEvent.ACTION_DRAG_EXITED;
import static android.view.DragEvent.ACTION_DRAG_LOCATION;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.inipage.homelylauncher.utils.DebugLogUtils.TAG_PAGE_SCROLL;
import static com.inipage.homelylauncher.utils.DebugLogUtils.TAG_POCKET_ANIMATION;
import static com.inipage.homelylauncher.utils.DebugLogUtils.TAG_WALLPAPER_OFFSET;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.viewpager2.widget.ViewPager2;
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback;

import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.caches.AppLabelCache;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.dock.DockController;
import com.inipage.homelylauncher.dock.ForwardingContainer;
import com.inipage.homelylauncher.drawer.HideAppEvent;
import com.inipage.homelylauncher.grid.GridPageController;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.pager.BasePageController;
import com.inipage.homelylauncher.pager.HomePager;
import com.inipage.homelylauncher.pager.NonTouchInputCoordinator;
import com.inipage.homelylauncher.pager.PagerIndicatorView;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.persistence.PrefsHelper;
import com.inipage.homelylauncher.pocket.PocketController;
import com.inipage.homelylauncher.pocket.PocketControllerDropView;
import com.inipage.homelylauncher.pocket.PocketOpenArrowView;
import com.inipage.homelylauncher.state.EditingEvent;
import com.inipage.homelylauncher.state.LayoutEditingSingleton;
import com.inipage.homelylauncher.state.PagesChangedEvent;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.SizeDimenAttribute;
import com.inipage.homelylauncher.utils.SizeValAttribute;
import com.inipage.homelylauncher.utils.StatusBarUtils;
import com.inipage.homelylauncher.utils.ViewUtils;
import com.inipage.homelylauncher.views.DecorViewDragger;
import com.inipage.homelylauncher.views.DecorViewManager;
import com.inipage.homelylauncher.views.ProvidesOverallDimensions;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import butterknife.BindView;
import butterknife.ButterKnife;

public class HomeActivity extends AppCompatActivity implements
    PocketController.Host,
    HomePager.Host,
    NonTouchInputCoordinator.Host,
    ProvidesOverallDimensions {

    public static final int REQUEST_BIND_APP_WIDGET = 300;
    public static final int REQUEST_CONFIGURE_WIDGET = 400;
    public static final int REQUEST_LOCATION_PERMISSION = 500;

    public static final long PAGE_SWITCH_DELAY = 750L;

    // TODO: Fix this
    private static final boolean DISABLE_WALLPAPER_OFFSET_CHANGING = true;

    @SizeValAttribute(20)
    int distanceFromEdgeToSwitchPages;
    @SizeDimenAttribute(R.dimen.actuation_distance)
    int actuationDistance;
    @BindView(R.id.rootView)
    ViewGroup rootView;
    @BindView(R.id.background_tint)
    View backgroundTint;
    @BindView(R.id.pocket_view_container)
    ForwardingContainer pocketContainer;
    @BindView(R.id.bottom_indicator)
    PocketOpenArrowView pocketIdleView;
    @BindView(R.id.pocket_drop_view)
    PocketControllerDropView pocketDropView;
    @BindView(R.id.pager_indicator_view)
    PagerIndicatorView pagerIndicatorView;
    @BindView(R.id.top_scrim)
    View topScrim;
    @BindView(R.id.bottom_scrim)
    View bottomScrim;
    @BindView(R.id.dock_container_scrollview)
    HorizontalScrollView dockViewScrollView;
    @BindView(R.id.dock_container)
    LinearLayout dockView;
    @BindView(R.id.forwarding_container)
    ForwardingContainer forwardingContainer;
    @BindView(R.id.pager_view)
    ViewPager2 pagerView;

    private HomePager mPager;
    private NonTouchInputCoordinator mNonTouchInputCoordinator;
    private final OnPageChangeCallback mOnPageChangeCallback = new OnPageChangeCallback() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            DebugLogUtils.needle(
                TAG_PAGE_SCROLL,
                "position=" + position + "; offset=" + positionOffset + "; px=" +
                    positionOffsetPixels);
            if (position == 0) {
                updateBackgroundAlpha(1 - positionOffset);
                mPager.getAppDrawerController().getView().setAlpha(1 - positionOffset);
                forwardingContainer.setTranslationX(
                    forwardingContainer.getWidth() - positionOffsetPixels);
                forwardingContainer.setAlpha(positionOffset);
                if (!mSyntheticScrolling) {
                    mPager.getAppDrawerController().quitSearch();
                }
            } else if (position >= 1) {
                dockView.setAlpha(1);
                forwardingContainer.setAlpha(1);
                updateBackgroundAlpha(0);
                forwardingContainer.setTranslationX(0);
            }

            // Fade each page in/out
            for (int i = 0; i < mPager.getItemCount(); i++) {
                BasePageController controller = mPager.getPageController(i);
                float targetAlpha = 0;
                if (i == position) {
                    targetAlpha = 1 - positionOffset;
                } else if (i == position - 1 || i == position + 1) {
                    targetAlpha = positionOffset;
                }
                @Nullable final View v =
                    controller.getDragAwareComponent().getDragAwareTargetView();
                if (v != null) {
                    v.setAlpha(targetAlpha);
                }
            }

            DecorViewDragger.get(HomeActivity.this).update();
            updateWallpaperOffset(position, positionOffset);
        }

        @Override
        public void onPageSelected(int position) {
            DebugLogUtils.needle(TAG_PAGE_SCROLL, "onPageSelected: " + position);
            pagerIndicatorView.updateActiveItem(position);
            for (int i = 0; i < mPager.getItemCount(); i++) {
                if (position == i) {
                    mPager.getPageController(position).onResume();
                } else {
                    mPager.getPageController(position).onPause();
                }
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            DebugLogUtils.needle(TAG_PAGE_SCROLL, "Scroll state = " + state);
            switch (state) {
                case ViewPager2.SCROLL_STATE_DRAGGING:
                case ViewPager2.SCROLL_STATE_SETTLING:
                    // User is dragging
                    break;
                case ViewPager2.SCROLL_STATE_IDLE:
                    // User stopped touching/screen stopped scrolling
                    mSyntheticScrolling = false;
                    break;
                default:
                    break;
            }
        }
    };
    private DockController mDockController;
    private PocketController mPocketController;
    private View.OnLayoutChangeListener mFirstLayoutListener;
    private boolean mHasSetPage = false;
    @Nullable private String mPendingGridPageId;
    private boolean mSyntheticScrolling = false;

    //region Android lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ButterKnife.bind(this, this.findViewById(R.id.rootView));
        AttributeApplier.applyDensity(this, this);

        mPager = new HomePager(this, rootView);
        mNonTouchInputCoordinator = new NonTouchInputCoordinator(this, this);
        pagerView.setAdapter(mPager);
        pagerView.registerOnPageChangeCallback(mOnPageChangeCallback);
        pagerView.setOffscreenPageLimit(100);
        pagerIndicatorView.setup(mPager.getItemCount() - 1);
        updateWallpaperOffsetSteps();
        pagerView.post(() -> updateWallpaperOffset(pagerView.getCurrentItem(), 0));

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().setNavigationBarColor(getResources().getColor(R.color.transparent));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        rootView.setFitsSystemWindows(false);
        mFirstLayoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (!mHasSetPage) {
                pagerView.setCurrentItem(1, false);
                mOnPageChangeCallback.onPageScrolled(1, 0, 0);
                mHasSetPage = true;
            }
            rootView.removeOnLayoutChangeListener(mFirstLayoutListener);
        };
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            int topScrim = insets.getSystemWindowInsetTop();
            int bottomScrim = insets.getSystemWindowInsetBottom();
            ViewUtils.setHeight(HomeActivity.this.topScrim, topScrim);
            ViewUtils.setHeight(HomeActivity.this.bottomScrim, bottomScrim);
            rootView.addOnLayoutChangeListener(mFirstLayoutListener);
            rootView.requestLayout();
            mPocketController.applyScrims(topScrim, bottomScrim);
            // New user experience
            if (PrefsHelper.checkAndUpdateIsNewUser(this)) {
                new NewUserBottomSheet(this).show();
            }
            return insets.consumeSystemWindowInsets();
        });
        DecorViewDragger.get(this)
            .registerDragAwareComponent(new DecorViewDragger.DragAwareComponent() {

                private final SwitchPageHandler mSwitchPageHandler = new SwitchPageHandler();

                @Nullable
                @Override
                public View getDragAwareTargetView() {
                    return rootView;
                }

                @Override
                public void onDrag(View v, DecorViewDragger.DragEvent event) {
                    switch (event.getAction()) {
                        case DragEvent.ACTION_DRAG_STARTED:
                            if (isOnAppDrawer()) {
                                switchPageRight();
                            }
                            break;
                        case ACTION_DRAG_ENTERED:
                        case ACTION_DRAG_LOCATION:
                            if (event.getRawX() < distanceFromEdgeToSwitchPages) {
                                mSwitchPageHandler.queueLeftSwitch();
                            } else if (event.getRawX() >
                                rootView.getWidth() - distanceFromEdgeToSwitchPages) {
                                @Nullable
                                final GridPageController currentPage = getSelectedGridPage();
                                if (currentPage != null && !currentPage.isEmptyPage()) {
                                    mSwitchPageHandler.queueRightSwitch();
                                }
                            }
                            break;
                        case ACTION_DRAG_EXITED:
                        case ACTION_DRAG_ENDED:
                            mSwitchPageHandler.clearMessages();
                            break;
                    }
                }

                @Override
                public int getPriority() {
                    return DecorViewDragger.DRAG_PRIORITY_LOWEST;
                }
            });

        mDockController = new DockController(dockView);
        mPocketController = new PocketController(
            getContext(),
            this,
            pocketContainer,
            dockView,
            pocketDropView,
            pocketIdleView);
        forwardingContainer.setForwardingListener(mPocketController);
        pocketContainer.setForwardingListener(mPocketController);
    }

    @Override
    public void onConfigurationChanged(@NotNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        IconCacheSync.getInstance(this).clearCache();
        AppLabelCache.getInstance(this).clearCache();
        mPager.getAppDrawerController().reloadAppList();
    }

    @Override
    protected void onStart() {
        super.onStart();
        AppInfoCache.get().getAppWidgetHost().startListening();
        mDockController.loadDock();
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        AppInfoCache.get().getAppWidgetHost().stopListening();
        mDockController.destroyDock();
        if (!LayoutEditingSingleton.getInstance().isEditing()) {
            pagerView.setCurrentItem(1);
        }
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    @Override
    public void commitNonTouchInput(@NonNull NonTouchInputCoordinator.NonTouchInputMessage msg) {
        switch (msg) {
            case EXPAND_POCKET:
                mPocketController.expand();
                break;
            case COLLAPSE_POCKET:
                mPocketController.collapse();
                break;
            case EXPAND_STATUS_BAR:
                StatusBarUtils.expandStatusBar(this);
                break;
            case SWITCH_RIGHT:
                pagerView.setCurrentItem(pagerView.getCurrentItem() + 1, true);
                break;
            case SWITCH_LEFT:
                pagerView.setCurrentItem(pagerView.getCurrentItem() - 1, true);
                break;
            case SWITCH_TO_HOME_SCREEN:
                pagerView.setCurrentItem(1, true);
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mNonTouchInputCoordinator.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        return mNonTouchInputCoordinator.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mNonTouchInputCoordinator.dispatchKeyEvent(event);
    }

    @Override
    public boolean defaultDispatchGenericMotionEvent(@NonNull MotionEvent ev) {
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean defaultDispatchKeyEvent(@NonNull KeyEvent ev) {
        return super.dispatchKeyEvent(ev);
    }

    @Override
    public boolean defaultOnKeyDown(int keyCode, @NonNull KeyEvent ev) {
        return super.onKeyDown(keyCode, ev);
    }

    @Nullable
    private GridPageController getSelectedGridPage() {
        final BasePageController pageController = mPager.getPageController(pagerView.getCurrentItem());
        if (pageController instanceof GridPageController) {
            return (GridPageController) pageController;
        }
        return null;
    }

    @Override
    public Activity getContext() {
        return this;
    }

    @Override
    public void requestBindWidget(
        String pageId, int appWidgetId, AppWidgetProviderInfo appWidgetProviderInfo) {
        mPendingGridPageId = pageId;

        final Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, appWidgetProviderInfo.provider);
        intent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        startActivityForResult(intent, REQUEST_BIND_APP_WIDGET);
    }

    @Override
    public void requestConfigureWidget(
        String pageId, int appWidgetId, AppWidgetProviderInfo appWidgetProviderInfo) {
        mPendingGridPageId = pageId;

        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        intent.setComponent(appWidgetProviderInfo.configure);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        try {
            startActivityForResult(intent, REQUEST_CONFIGURE_WIDGET);
        } catch (SecurityException configureComponentNotExported) {
            // Happens with At a Glance widget, potentially others
            mPager.getGridController(mPendingGridPageId).commitPendingWidgetAddition();
        }
    }

    @Override
    public void forwardSwipeUp(MotionEvent event, float deltaY) {
        if (LayoutEditingSingleton.getInstance().isEditing()) {
            return;
        }
        mPocketController.onForwardEvent(event, -deltaY);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case REQUEST_BIND_APP_WIDGET:
                mPager.getGridController(mPendingGridPageId).onBindWidgetSucceeded();
                break;
            case REQUEST_CONFIGURE_WIDGET:
                mPager.getGridController(mPendingGridPageId).commitPendingWidgetAddition();
                break;
            case REQUEST_LOCATION_PERMISSION:
                mDockController.loadDock();
                break;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getAction().equals(Intent.ACTION_MAIN)) {
            if (DecorViewManager.get(this).detachAllViews()) {
                return;
            }
            if (mPocketController.isExpanded()) {
                mPocketController.collapse();
                return;
            }
            if (!isOnFirstHomeScreen()) {
                pagerView.setCurrentItem(1, true);
                return;
            }
            if (LayoutEditingSingleton.getInstance().isEditing()) {
                LayoutEditingSingleton.getInstance().setEditing(false);
                return;
            }
            dockViewScrollView.smoothScrollTo(0, 0);
        }
    }

    private void switchPageLeft() {
        if (isOnAppDrawer()) {
            return;
        }
        pagerView.setCurrentItem(pagerView.getCurrentItem() - 1, true);
    }

    @Override
    public void editFolderOrder() {
        mPocketController.editFolderOrder();
    }

    @Override
    public void requestAppDrawerFocus() {
        if (pagerView.getCurrentItem() == 0) {
            return;
        }
        mSyntheticScrolling = true;
        pagerView.setCurrentItem(0, true);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPagesChangedEvent(PagesChangedEvent event) {
        pagerIndicatorView.setup(event.getNewPageCount());
        pagerView.setCurrentItem(event.getNewPageCount(), true);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEditingEvent(EditingEvent event) {
        backgroundTint.animate().alpha(event.isEditing() ? 1 : 0);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onHideAppEvent(HideAppEvent hideAppEvent) {
        final ApplicationIcon ai = hideAppEvent.app();
        DatabaseEditor.get().markAppHidden(ai.getActivityName(), ai.getPackageName());
        AppInfoCache.get().reloadVisibleActivities();
        mPager.getAppDrawerController().hideApp(ai);
    }

    @Override
    public void onPartiallyExpandedPocket(float percent) {
        DebugLogUtils.needle(TAG_POCKET_ANIMATION, "onPartiallyExpandedPocket " + percent);

        forwardingContainer.setVisibility(VISIBLE);
        dockViewScrollView.setAlpha(1 - percent);
        pagerIndicatorView.setAlpha(1 - percent);
        pocketIdleView.setAlpha(1 - percent);
        pagerView.setAlpha(1 - percent);
        pagerView.setScaleX(1 - (percent * PocketController.Companion.getSCALE_DELTA()));
        pagerView.setScaleY(1 - (percent * PocketController.Companion.getSCALE_DELTA()));
        pagerView.setTranslationY(-actuationDistance * percent);

        updateBackgroundAlpha(percent);
    }

    @Override
    public void onPocketExpanded() {
        DebugLogUtils.needle(TAG_POCKET_ANIMATION, "onPocketExpanded");

        forwardingContainer.setVisibility(GONE);
        dockViewScrollView.setAlpha(0);
        pagerIndicatorView.setAlpha(0);
        pocketIdleView.setAlpha(0);
        pagerView.setAlpha(0);
        pagerView.setScaleX(1 - PocketController.Companion.getSCALE_DELTA());
        pagerView.setScaleY(1 - PocketController.Companion.getSCALE_DELTA());
        pagerView.setTranslationY(-actuationDistance);
        updateBackgroundAlpha(1);
    }

    @Override
    public void onPocketCollapsed() {
        DebugLogUtils.needle(TAG_POCKET_ANIMATION, "onPocketCollapsed");

        forwardingContainer.setVisibility(VISIBLE);
        dockViewScrollView.setAlpha(1);
        pagerIndicatorView.setAlpha(1);
        pocketIdleView.setAlpha(1);
        pagerView.setAlpha(1);
        pagerView.setScaleX(1);
        pagerView.setScaleY(1);
        pagerView.setTranslationY(0);
        updateBackgroundAlpha(0);
    }

    @Override
    public void clearActiveDragTarget() {
        final BasePageController pageController =
            mPager.getPageController(pagerView.getCurrentItem());
        if (pageController instanceof GridPageController) {
            ((GridPageController) pageController).clearDragTarget();
        }
    }

    @Override
    public Pair<Integer, Integer> provideScrims() {
        return new Pair<>(topScrim.getHeight(), bottomScrim.getHeight());
    }

    @Override
    public Rect provideOverallBounds() {
        final int[] out = new int[2];
        backgroundTint.getLocationOnScreen(out);
        return new Rect(
            out[0],
            out[1],
            out[0] + backgroundTint.getWidth(),
            out[1] + backgroundTint.getHeight());
    }

    @Override
    public boolean isOnAppDrawer() {
        return pagerView.getCurrentItem() == 0;
    }

    @Override
    public boolean isOnLastPage() {
        return pagerView.getCurrentItem() == mPager.getItemCount() - 1;
    }

    @Override
    public boolean isOnFirstHomeScreen() {
        return pagerView.getCurrentItem() == 1;
    }

    @NonNull
    @Override
    public HomePager getPager() {
        return mPager;
    }

    @Override
    public boolean isPocketExpanded() {
        return mPocketController.isExpanded();
    }

    @Override
    public boolean isAlphabeticalPickerOpen() {
        return mPager.getAppDrawerController().isAlphabeticalPickerOpen();
    }

    private void updateWallpaperOffsetSteps() {
        final WallpaperManager wm = WallpaperManager.getInstance(this);
        if (wm.getWallpaperInfo() == null) {
            return;
        }
        if (DISABLE_WALLPAPER_OFFSET_CHANGING) {
            return;
        }
        wm.setWallpaperOffsetSteps(mPager.getWallpaperOffsetSteps(), 0);
        DebugLogUtils.needle(
            TAG_WALLPAPER_OFFSET,
            "Offset steps:",
            String.valueOf(mPager.getWallpaperOffsetSteps()));
    }

    private void updateWallpaperOffset(int currentItem, float marginalOffset) {
        final WallpaperManager wm = WallpaperManager.getInstance(HomeActivity.this);
        if (wm.getWallpaperInfo() == null) {
            return;
        }
        if (DISABLE_WALLPAPER_OFFSET_CHANGING) {
            return;
        }
        wm.setWallpaperOffsets(
            pagerView.getWindowToken(),
            mPager.getWallpaperOffset(currentItem, marginalOffset),
            0);
        DebugLogUtils.needle(
            TAG_WALLPAPER_OFFSET,
            "Update wallpaper offset:",
            String.valueOf(mPager.getWallpaperOffset(currentItem, marginalOffset)));
    }

    private void switchPageRight() {
        final int currentPage = pagerView.getCurrentItem();
        if (isOnLastPage()) {
            mPager.spawnNewPage();
            updateWallpaperOffsetSteps();
        } else {
            pagerView.setCurrentItem(currentPage + 1, true);
        }
    }

    private void updateBackgroundAlpha(float newAlpha) {
        backgroundTint.setAlpha(
            LayoutEditingSingleton.getInstance().isEditing() ? Math.max(newAlpha, 1) : newAlpha);
    }

    private class SwitchPageHandler implements Handler.Callback {

        private static final int MSG_MOVE_RIGHT = 1;
        private static final int MSG_MOVE_LEFT = 2;

        private final Handler mHandler;

        SwitchPageHandler() {
            mHandler = new Handler(Looper.getMainLooper(), this);
        }

        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_MOVE_LEFT:
                    switchPageLeft();
                    return true;
                case MSG_MOVE_RIGHT:
                    switchPageRight();
                    return false;
            }
            return false;
        }

        public synchronized void queueLeftSwitch() {
            if (mHandler.hasMessages(MSG_MOVE_LEFT)) {
                return;
            }
            mHandler.sendMessageDelayed(
                Message.obtain(mHandler, MSG_MOVE_LEFT), PAGE_SWITCH_DELAY);
        }

        public synchronized void queueRightSwitch() {
            if (mHandler.hasMessages(MSG_MOVE_RIGHT)) {
                return;
            }
            mHandler.sendMessageDelayed(
                Message.obtain(mHandler, MSG_MOVE_RIGHT), PAGE_SWITCH_DELAY);
        }

        public synchronized void clearMessages() {
            mHandler.removeCallbacksAndMessages(null);
        }
    }
}