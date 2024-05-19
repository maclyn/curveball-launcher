package com.inipage.homelylauncher;

import static android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL;
import static android.view.DragEvent.ACTION_DRAG_ENDED;
import static android.view.DragEvent.ACTION_DRAG_ENTERED;
import static android.view.DragEvent.ACTION_DRAG_LOCATION;
import static com.inipage.homelylauncher.utils.DebugLogUtils.TAG_ICON_CASCADE;
import static com.inipage.homelylauncher.utils.DebugLogUtils.TAG_PAGE_SCROLL;
import static com.inipage.homelylauncher.utils.DebugLogUtils.TAG_WALLPAPER_OFFSET;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback;

import com.google.common.collect.ImmutableList;
import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.caches.AppLabelCache;
import com.inipage.homelylauncher.caches.FontCacheSync;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.dock.DockController;
import com.inipage.homelylauncher.drawer.HideAppEvent;
import com.inipage.homelylauncher.folders.FolderController;
import com.inipage.homelylauncher.grid.AppViewHolder;
import com.inipage.homelylauncher.grid.ClassicGridPageController;
import com.inipage.homelylauncher.hacks.FasterPagerSnapHelper;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.ClassicGridPage;
import com.inipage.homelylauncher.pager.BasePageController;
import com.inipage.homelylauncher.pager.HomePager;
import com.inipage.homelylauncher.pager.NonTouchInputCoordinator;
import com.inipage.homelylauncher.pager.PagerIndicatorView;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.persistence.PrefsHelper;
import com.inipage.homelylauncher.state.EditingEvent;
import com.inipage.homelylauncher.state.GestureNavContractSingleton;
import com.inipage.homelylauncher.state.LayoutEditingSingleton;
import com.inipage.homelylauncher.state.PagesChangedEvent;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.Constants;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.SizeDimenAttribute;
import com.inipage.homelylauncher.utils.StatusBarUtils;
import com.inipage.homelylauncher.utils.ViewUtils;
import com.inipage.homelylauncher.views.DecorViewDragger;
import com.inipage.homelylauncher.views.DecorViewManager;
import com.inipage.homelylauncher.views.DraggableLayout;
import com.inipage.homelylauncher.views.ProvidesOverallDimensions;
import com.inipage.homelylauncher.views.SurfaceViewWrapper;
import com.inipage.homelylauncher.widgets.WidgetHost;
import com.inipage.homelylauncher.widgets.WidgetLifecycleUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

import butterknife.BindView;
import butterknife.ButterKnife;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

@SuppressLint("NonConstantResourceId")
public class HomeActivity extends AppCompatActivity implements
    FolderController.Host,
    HomePager.Host,
    DockController.Host,
    NonTouchInputCoordinator.Host,
    WidgetHost,
    ProvidesOverallDimensions
{

    private class NavContractClosedReceiver implements Handler.Callback {
        private static final int MSG_CLOSE_LAST_TARGET = 0;

        private final Messenger mMessenger = new Messenger(new Handler(Looper.getMainLooper(), this));

        @Override
        public boolean handleMessage(@NonNull Message message) {
            if (message.what == MSG_CLOSE_LAST_TARGET) {
                mSurfaceViewWrapper.hide();
                return true;
            }
            return false;
        }

        public Message buildMessage() {
            Message msg = Message.obtain();
            msg.replyTo = mMessenger;
            msg.what = MSG_CLOSE_LAST_TARGET;
            return msg;
        }
    }

    private NavContractClosedReceiver mNavContractClosedReceiver = null;

    public static final int REQUEST_BIND_APP_WIDGET = 300;
    public static final int REQUEST_CONFIGURE_WIDGET = 400;
    public static final int REQUEST_LOCATION_PERMISSION = 500;

    public static final long PAGE_SWITCH_DELAY = 1000L;

    // TODO: Fix this
    private static final boolean DISABLE_WALLPAPER_OFFSET_CHANGING = true;

    @SizeDimenAttribute(R.dimen.dist_from_edge_to_switch)
    int distanceFromEdgeToSwitchPages;

    @BindView(R.id.rootView)
    ViewGroup rootView;
    @BindView(R.id.background_tint)
    View backgroundTint;
    @BindView(R.id.top_scrim_gradient)
    View scrimGradient;
    @BindView(R.id.pager_indicator_view)
    PagerIndicatorView pagerIndicatorView;
    @BindView(R.id.top_scrim)
    View topScrim;
    @BindView(R.id.bottom_scrim)
    View bottomScrim;
    @BindView(R.id.dock_container)
    RecyclerView dockView;
    @BindView(R.id.dock_element_container)
    RelativeLayout dockElementContainer;
    @BindView(R.id.pager_view)
    ViewPager2 pagerView;
    @BindView(R.id.veil)
    View folderVeil;
    @BindView(R.id.folder_container)
    DraggableLayout folderContainer;

    private ImmutableList<View> folderTranslatableElements;

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
                dockElementContainer.setTranslationX(
                    dockElementContainer.getWidth() - positionOffsetPixels);
                dockElementContainer.setAlpha(positionOffset);
                if (!mSyntheticScrolling) {
                    mPager.getAppDrawerController().quitSearch();
                }
            } else if (position >= 1) {
                dockView.setAlpha(1);
                dockElementContainer.setAlpha(1);
                updateBackgroundAlpha(0);
                dockElementContainer.setTranslationX(0);
            }

            // Fade each page in/out
            for (int i = 0; i < mPager.getItemCount(); i++) {
                if (i < position - 1 || i > position + 1) {
                    continue;
                }

                BasePageController controller = mPager.getPageController(i);
                float dilutionAmount;
                if (i == position) {
                    dilutionAmount = positionOffset;
                } else {
                    // The bigger the offset, the closer we are
                    dilutionAmount = 1 - positionOffset;
                }
                @Nullable final View v =
                    controller.getDragAwareComponent().getDragAwareTargetView();
                if (v != null) {
                    // Range from 0.5 to 1, so the effect isn't too pronounced
                    v.setAlpha(1 - (dilutionAmount / 2.0F));
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

    private final DecorViewDragger.DragAwareComponent mBackgroundDragAwareComponent =
        new DecorViewDragger.DragAwareComponent() {

            private final SwitchPageHandler mSwitchPageHandler = new SwitchPageHandler();

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
                            @Nullable
                            final ClassicGridPageController currentPage = getSelectedGridPage();
                            if (currentPage != null && !mSyntheticScrolling) {
                                mSwitchPageHandler.queueLeftSwitch();
                            }
                        } else if (event.getRawX() > rootView.getWidth() - distanceFromEdgeToSwitchPages) {
                            @Nullable
                            final ClassicGridPageController currentPage = getSelectedGridPage();
                            if (currentPage != null && !currentPage.isEmptyPage() && !mSyntheticScrolling) {
                                mSwitchPageHandler.queueRightSwitch();
                            }
                        } else {
                            mSwitchPageHandler.clearMessages();
                        }
                        break;
                    case ACTION_DRAG_ENDED:
                        mSwitchPageHandler.clearMessages();
                        break;
                }
            }
        };

    private DockController mDockController;
    private FolderController mFolderController;
    private View.OnLayoutChangeListener mFirstLayoutListener;
    private boolean mHasSetPage = false;
    private boolean mSyntheticScrolling = false;

    @Nullable private WidgetHost.SourceData mPendingWidgetActionRoutingData;


    private SurfaceViewWrapper mSurfaceViewWrapper;

    //region Android lifecycle
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ButterKnife.bind(this, this.findViewById(R.id.rootView));
        folderTranslatableElements = ImmutableList.of(
            pagerView,
            dockElementContainer
        );
        AttributeApplier.applyDensity(this, this);
        FontCacheSync.Companion.get().reload(this);
        setRequestedOrientation(ViewUtils.isTablet(this) ?
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED :
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                recreate();
            }
        }, new IntentFilter(Constants.INTENT_ACTION_RESTART));

        mSurfaceViewWrapper = new SurfaceViewWrapper(this);

        mPager = new HomePager(this, rootView);
        mNonTouchInputCoordinator = new NonTouchInputCoordinator(this, this);
        FasterPagerSnapHelper.Companion.apply(pagerView);
        pagerView.setAdapter(mPager);
        pagerView.registerOnPageChangeCallback(mOnPageChangeCallback);
        pagerView.setOffscreenPageLimit(100);
        pagerIndicatorView.setup(mPager.getItemCount() - 1);
        folderVeil.setOnClickListener(v -> mFolderController.closeFolder());
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

            // New user experience
            ViewUtils.waitForLayoutToTakeSpace(HomeActivity.this.topScrim, () -> {
                if (PrefsHelper.get().checkAndUpdateIsNewUser()) {
                    new NewUserBottomSheet(HomeActivity.this).show();
                }
                mFolderController.onHomeActivitySized();
                return null;
            });

            return insets.consumeSystemWindowInsets();
        });
        DecorViewDragger
            .get(this)
            .registerBackgroundDragAwareComponent(mBackgroundDragAwareComponent);

        mDockController = new DockController(dockView, this);
        mFolderController = new FolderController(getContext(), this, folderContainer);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DecorViewDragger
            .get(this)
            .unregisterBackgroundDragAwareComponent(mBackgroundDragAwareComponent);
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
        // Dock has its own animation
        mDockController.loadDock();

        // Animate everything in a little
        // Start at transparent and a little smaller
        scrimGradient.setAlpha(0);
        pagerIndicatorView.setAlpha(0);
        pagerView.setAlpha(0F);
        pagerView.setScaleX(0.9F);
        pagerView.setScaleY(0.9F);

        // 0 -> 1 alpha on these components
        scrimGradient.animate().alpha(1F).start();
        pagerIndicatorView.animate().alpha(1F).start();
        // Grow the pager in
        pagerView.animate()
            .alpha(1F)
            .scaleX(1F)
            .scaleY(1F)
            .start();

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        AppInfoCache.get().getAppWidgetHost().stopListening();
        mDockController.destroyDock();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        GestureNavContractSingleton.INSTANCE.onHomeActivityStopped();
        if (GestureNavContractSingleton.INSTANCE.lastValidComponentLaunch() == null) {
            pagerView.setCurrentItem(1, false);
            mOnPageChangeCallback.onPageScrolled(1, 0, 0);
        }
    }

    @Override
    public void commitNonTouchInput(@NonNull NonTouchInputCoordinator.NonTouchInputMessage msg) {
        switch (msg) {
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
            case CLOSE_FOLDER:
                mFolderController.closeFolder();
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
    private ClassicGridPageController getSelectedGridPage() {
        final BasePageController pageController = mPager.getPageController(pagerView.getCurrentItem());
        if (pageController instanceof ClassicGridPageController) {
            return (ClassicGridPageController) pageController;
        }
        return null;
    }

    @Override
    public Activity getContext() {
        return this;
    }

    @Override
    public void requestBindWidget(
        int appWidgetId,
        @NonNull AppWidgetProviderInfo awpi,
        @NonNull SourceData sourceData)
    {
        mPendingWidgetActionRoutingData = sourceData;

        final Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, awpi.provider);
        intent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        startActivityForResult(intent, REQUEST_BIND_APP_WIDGET);
    }

    @Override
    public void requestConfigureWidget(
        int appWidgetId,
        @NonNull AppWidgetProviderInfo awpi,
        @NonNull SourceData sourceData)
    {
        mPendingWidgetActionRoutingData = sourceData;

        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        intent.setComponent(awpi.configure);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        try {
            startActivityForResult(intent, REQUEST_CONFIGURE_WIDGET);
        } catch (SecurityException configureComponentNotExported) {
            // Happens with At a Glance widget, potentially others
            switch (sourceData.getSource()) {
                case FolderController:
                    // TODO
                    break;
                case GridPageController:
                    mPager.getGridController(sourceData.getPageId()).commitPendingWidgetAddition();
                    break;
            }
        }
    }

    @Override
    public void onSwipeUpStarted(
        AppViewHolder appViewHolder,
        MotionEvent motionEvent,
        View sourceView,
        int firstPointerIdx,
        float startRawY
    ) {
        mFolderController.onStartOpenAction(
            appViewHolder, motionEvent, sourceView,firstPointerIdx, startRawY);
    }

    @Override
    public void onSwipeUpMotionEvent(
        MotionEvent event,
        int action,
        int firstPointerId,
        float startRawY
    ) {
        mFolderController.onOpenMotionEvent(event, action, firstPointerId);
    }

    @NonNull
    @Override
    public List<ClassicGridPage> getGridPages() {
        return mPager.getGridPages();
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_BIND_APP_WIDGET) {
                WidgetLifecycleUtils.endTransaction();
            } else if (requestCode == REQUEST_CONFIGURE_WIDGET) {
                @Nullable WidgetLifecycleUtils.WidgetAddTransaction transaction =
                    WidgetLifecycleUtils.INSTANCE.getActiveTransaction();
                if (transaction == null) {
                    return;
                }
                @SuppressLint("InlinedApi")
                boolean markedAsNonConfigurable = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ||
                    (transaction.getApwi().widgetFeatures & WIDGET_FEATURE_CONFIGURATION_OPTIONAL) == 0;

                // Some widgets (cough cough Google Weather) claim to require configuration but don't
                // export the component to do so. They add fine anyways.

                if (markedAsNonConfigurable) {
                    DebugLogUtils.needle(TAG_ICON_CASCADE, "Widget configuration non-optional but going ahead anyways");
                }
                completeWidgetConfiguration();
            }
            return;
        }
        switch (requestCode) {
            case REQUEST_BIND_APP_WIDGET:
                completeWidgetBinding();
                break;
            case REQUEST_CONFIGURE_WIDGET:
                completeWidgetConfiguration();
                break;
            case REQUEST_LOCATION_PERMISSION:
                mDockController.loadDock();
                break;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        @Nullable String action = intent.getAction();
        if (action == null) {
            return;
        }
        if (action.equals(Intent.ACTION_MAIN)) {
            if (DecorViewManager.get(this).detachAllViews()) {
                return;
            }

            // Bring dock to start
            @Nullable RecyclerView.LayoutManager layoutManager = dockView.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(0, 0);
            }

            // try to handle GestureNavContract
            @Nullable Bundle navContract = intent.getBundleExtra("gesture_nav_contract_v1");
            boolean handledNavContract = false;
            if (navContract != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                handledNavContract = handleGestureNavContract(navContract);
            }
            if (handledNavContract) {
                return;
            }

            // Otherwise, cascade to resetting everything
            if (!isOnFirstHomeScreen()) {
                pagerView.setCurrentItem(1, true);
                return;
            }
            if (LayoutEditingSingleton.getInstance().isEditing()) {
                LayoutEditingSingleton.getInstance().setEditing(false);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private boolean handleGestureNavContract(Bundle navContract) {
        @Nullable ComponentName componentName = navContract.getParcelable("android.intent.extra.COMPONENT_NAME");
        @Nullable Message callback = navContract.getParcelable("android.intent.extra.REMOTE_CALLBACK");
        if (componentName != null && callback != null) {
            // Check if we have a likely launch
            @Nullable GestureNavContractSingleton.ComponentLaunch componentLaunch =
                GestureNavContractSingleton.INSTANCE.lastValidComponentLaunch();
            if (componentLaunch == null) {
                return false;
            }
            GestureNavContractSingleton.INSTANCE.clearComponentLaunch();

            Bundle result = new Bundle();
            result.putParcelable("gesture_nav_contract_icon_position", componentLaunch.getPosition());
            result.putParcelable(
                "gesture_nav_contract_surface_control",
                mSurfaceViewWrapper.show().getSurfaceControl());
            if (mNavContractClosedReceiver == null) {
                mNavContractClosedReceiver = new NavContractClosedReceiver();
            }
            result.putParcelable(
                "gesture_nav_contract_finish_callback",
                mNavContractClosedReceiver.buildMessage());

            Message response = Message.obtain();
            response.copyFrom(callback);
            response.setData(result);
            try {
                callback.replyTo.send(response);
                return true;
            } catch (RemoteException e) {
                Log.e("HomeActivity", "GestureNavContract error", e);
            }
        }
        return false;
    }


    @Override
    public void onStartFolderOpen() {
        folderVeil.setVisibility(View.VISIBLE);
        bottomScrim.setBackgroundColor(getColor(R.color.folder_background));
        bottomScrim.setAlpha(0.0F);
    }

    @Override
    public void onFolderPartiallyOpen(float percent, float translationAmount) {
        folderTranslatableElements.forEach(view -> view.setTranslationY(-translationAmount));
        folderVeil.setAlpha(percent);
        bottomScrim.setAlpha(percent);
    }

    @Override
    public void onFolderCompletelyOpen(float translationAmount) {
        onFolderPartiallyOpen(1.0F, translationAmount);
    }

    @Override
    public void onFolderClosed() {
        folderVeil.setVisibility(View.GONE);
        bottomScrim.setBackgroundColor(getColor(R.color.transparent));
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
        mSyntheticScrolling = true;
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
    public Pair<Integer, Integer> provideVerticalScrims() {
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
    public Pair<Integer, Integer> provideScrimYPositionsOnScreen() {
        final int[] out = new int[2];
        topScrim.getLocationOnScreen(out);
        final int topScrimPosition = out[1];
        bottomScrim.getLocationOnScreen(out);
        final int bottomScrimPosition = out[1];
        return Pair.create(topScrimPosition, bottomScrimPosition);
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
    public boolean isAlphabeticalPickerOpen() {
        return mPager.getAppDrawerController().isAlphabeticalPickerOpen();
    }

    @Override
    public boolean isFolderOpen() {
        return mFolderController.isFolderOpen();
    }

    private void switchPageLeft() {
        if (isOnFirstHomeScreen() || isOnAppDrawer()) {
            return;
        }
        mSyntheticScrolling = true;
        pagerView.setCurrentItem(pagerView.getCurrentItem() - 1, true);
    }

    private void switchPageRight() {
        final int currentPage = pagerView.getCurrentItem();
        if (isOnLastPage()) {
            mPager.spawnNewPage();
            updateWallpaperOffsetSteps();
        } else {
            mSyntheticScrolling = true;
            pagerView.setCurrentItem(currentPage + 1, true);
        }
    }

    private void completeWidgetBinding() {
        if (mPendingWidgetActionRoutingData == null) {
            return;
        }
        switch (mPendingWidgetActionRoutingData.getSource()) {
            case FolderController:
                mFolderController.onWidgetBound();
                break;
            case GridPageController:
                mPager.getGridController(
                    mPendingWidgetActionRoutingData.getPageId()).onBindWidgetSucceeded();
                break;
        }
        mPendingWidgetActionRoutingData = null;
    }

    private void completeWidgetConfiguration() {
        if (mPendingWidgetActionRoutingData == null) {
            return;
        }
        switch (mPendingWidgetActionRoutingData.getSource()) {
            case FolderController:
                mFolderController.onWidgetConfigureComplete();
                break;
            case GridPageController:
                mPager.getGridController(mPendingWidgetActionRoutingData.getPageId())
                    .commitPendingWidgetAddition();
                break;
        }
        mPendingWidgetActionRoutingData = null;
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
                    return true;
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