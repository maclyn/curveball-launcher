package com.inipage.homelylauncher.drawer;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.inipage.homelylauncher.BuildConfig;
import com.inipage.homelylauncher.NewUserBottomSheet;
import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.SettingsActivity;
import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.caches.PackageModifiedEvent;
import com.inipage.homelylauncher.caches.PackagesBulkModifiedEvent;
import com.inipage.homelylauncher.grid.AppViewHolder;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.pager.BasePageController;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.utils.InstalledAppUtils;
import com.inipage.homelylauncher.utils.ViewUtils;
import com.inipage.homelylauncher.views.BottomSheetHelper;
import com.inipage.homelylauncher.views.DecorViewDragger;
import com.inipage.homelylauncher.views.DecorViewManager;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnEditorAction;
import butterknife.OnTextChanged;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * The controller instantiates the conventional vertically scrolling app drawer provided by the
 * default launcher {@linkplain com.inipage.homelylauncher.HomeActivity}.
 */
public class AppDrawerController implements BasePageController, FastScrollController.Host {

    private final Host mHost;
    private final Context mContext;
    private final FastScrollController mScrollController;
    private final LinearLayoutManager mLayoutManager;
    @BindView(R.id.all_apps_root_view)
    View rootView;
    @BindView(R.id.all_apps_layout)
    RecyclerView appRecyclerView;
    @BindView(R.id.store_search_button)
    Button storeSearchButton;
    @BindView(R.id.app_drawer_action_bar)
    View actionBar;
    @BindView(R.id.search_box)
    EditText searchBox;
    @BindView(R.id.search_pull_layout)
    SwipeRefreshLayout searchPullLayout;

    private ApplicationIconAdapter mAdapter;
    // This flag isn't necessarily synced with mMode == SEARCH_RESULTS in the adapter; this flag
    // indicates a visible search box but not per se text entered and search results displayed
    private boolean mIsSearching = false;
    private boolean mInDrag = false;
    private final DecorViewDragger.DragAwareComponent mDragAwareComponent =
        new DecorViewDragger.DragAwareComponent() {

            @Override
            public View getDragAwareTargetView() {
                return appRecyclerView;
            }

            @Override
            public void onDrag(View v, DecorViewDragger.DragEvent event) {
                if (!(event.getLocalState() instanceof AppViewHolder)) {
                    return;
                }
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        mInDrag = true;
                        if (mIsSearching) {
                            hideKeyboard();
                        }
                        break;
                    case DragEvent.ACTION_DRAG_ENDED:
                        mInDrag = false;
                        quitSearch();
                        break;
                }
            }

            @Override
            public int getPriority() {
                return DecorViewDragger.DRAG_PRIORITY_LOWEST;
            }
        };

    private final ApplicationIconAdapter.Delegate mAdapterDelegate = new ApplicationIconAdapter.Delegate() {
        @Override
        public void enterSearchMode(View v) {
            enterSearch();
        }

        @Override
        public void enterFastScrollMode(View v) {
            mScrollController.enterFastScroll();
        }

        @Override
        public void showOptionsMenu(View v) {
            showOptionsBottomSheet(v);
        }

        @Override
        public void scrollToIndex(int idx) {
            appRecyclerView.scrollToPosition(idx);
        }

        @Override
        public int getFirstIndexOnScreen() {
            return mLayoutManager.findFirstVisibleItemPosition();
        }

        @Override
        public int getLastIndexOnScreen() {
            return mLayoutManager.findLastVisibleItemPosition();
        }

        @Override
        public int getTotalCount() {
            return mLayoutManager.getItemCount();
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    public AppDrawerController(Host host, ViewGroup rootView) {
        mHost = host;
        mContext = rootView.getContext();
        mScrollController = new FastScrollController(this);

        final LayoutInflater inflater = LayoutInflater.from(mContext);
        final View appDrawerView = inflater.inflate(R.layout.app_drawer_view, rootView, false);
        ButterKnife.bind(this, appDrawerView);

        DrawableCompat.setTint(
            searchBox.getBackground(),
            ContextCompat.getColor(mContext, R.color.primary_text_color));
        mLayoutManager = new LinearLayoutManager(mContext, RecyclerView.VERTICAL, false);
        appRecyclerView.setLayoutManager(mLayoutManager);
        appRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    rootView.getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
        });
        appRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                final int firstItem = mLayoutManager.findFirstVisibleItemPosition();
                final int lastItem = mLayoutManager.findLastVisibleItemPosition();
                final int firstCompletelyVisibleItem =
                    mLayoutManager.findFirstCompletelyVisibleItemPosition();
                final int lastCompletelyVisibleItem =
                    mLayoutManager.findLastCompletelyVisibleItemPosition();
                for (int idx = firstItem; idx <= lastItem; idx++) {
                    RecyclerView.ViewHolder holder =
                        appRecyclerView.findViewHolderForAdapterPosition(idx);
                    if (!(holder instanceof ApplicationIconAdapter.AnimatableViewHolder)) {
                        continue;
                    }
                    ApplicationIconAdapter.AnimatableViewHolder iconHolder =
                        (ApplicationIconAdapter.AnimatableViewHolder) holder;
                    if (idx == firstItem && firstItem != firstCompletelyVisibleItem ||
                        idx == lastItem && lastItem != lastCompletelyVisibleItem) {
                        iconHolder.applyAlpha(recyclerView);
                    } else {
                        iconHolder.view.setAlpha(1F);
                    }
                }
            }
        });
        searchPullLayout.setOnRefreshListener(() -> {
            searchPullLayout.setRefreshing(false);
            if (!mIsSearching) {
                enterSearch();
            }
        });
        setSearchDrawable();
        reloadAppList();
    }

    @Override
    public void onPause() {
        appRecyclerView.setScrollY(0);
    }

    public void reloadAppList() {
        if (mIsSearching) {
            quitSearch();
        }
        mAdapter = new ApplicationIconAdapter(mAdapterDelegate, ViewUtils.activityOf(mContext));
        mAdapter.setHasStableIds(true);
        appRecyclerView.setAdapter(mAdapter);
    }

    public void quitSearch() {
        if (!mIsSearching || mInDrag) {
            return;
        }
        searchBox.setText("");
        searchBox.clearFocus();
        searchBox.setFocusable(false);
        searchBox.setFocusableInTouchMode(false);
        hideKeyboard();
        mIsSearching = false;
        actionBar.setVisibility(GONE);
        storeSearchButton.setVisibility(GONE);
        mLayoutManager.setReverseLayout(false);
        searchPullLayout.setEnabled(true);
        mAdapter.leaveSearch();
        appRecyclerView.post(() -> appRecyclerView.setItemAnimator(new DefaultItemAnimator()));
    }

    private void setSearchDrawable() {
        try {
            final Field circleViewField = searchPullLayout.getClass().getDeclaredField("mCircleView");
            circleViewField.setAccessible(true);
            @Nullable final ImageView circleView = (ImageView) circleViewField.get(searchPullLayout);
            if (circleView != null) {
                circleView.setImageResource(R.drawable.ic_search_wrapper);
            }
        } catch (Exception ignored) {} // NoSuchElement/IllegalAccessException, most likely
    }

    private void hideKeyboard() {
        @Nullable InputMethodManager imm =
            (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(actionBar.getWindowToken(), 0);
        }
    }

    @Override
    public DecorViewDragger.DragAwareComponent getDragAwareComponent() {
        return mDragAwareComponent;
    }

    @OnClick(R.id.close_search_button)
    public void onCloseSearchButtonClicked(View view) {
        quitSearch();
    }

    private void enterSearch() {
        if (mIsSearching) {
            return;
        }

        mIsSearching = true;
        appRecyclerView.setItemAnimator(null);
        actionBar.setVisibility(VISIBLE);
        searchBox.setFocusable(true);
        searchBox.setFocusableInTouchMode(true);
        searchBox.requestFocus();
        searchPullLayout.setEnabled(false);
        showKeyboard();
        onSearchChanged("", 0, 0, 0);
    }

    private void showKeyboard() {
        @Nullable InputMethodManager imm =
            (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void showOptionsBottomSheet(View view) {
        final BottomSheetHelper bottomSheetHelper = new BottomSheetHelper()
            .addItem(
                R.drawable.ic_visibility_off_white_48dp,
                R.string.hidden_apps_setting_title,
                this::showHideAppsMenu)
            .addItem(
                R.drawable.ic_reorder,
                R.string.reorder_pocket_items,
                mHost::editFolderOrder)
            .addItem(R.drawable.ic_wallpaper_24, R.string.wallpaper_settings_title, () -> {
                final Intent pickWallpaper = new Intent(Intent.ACTION_SET_WALLPAPER);
                pickWallpaper.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                ViewUtils.activityOf(view.getContext()).startActivity(
                    Intent.createChooser(
                        pickWallpaper,
                        mContext.getString(R.string.set_wallpaper)));
            })
            .addItem(R.drawable.ic_settings_48, R.string.more_settings, () -> {
                final Intent settingsActivity = new Intent(mContext, SettingsActivity.class);
                settingsActivity.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                ViewUtils.activityOf(mContext).startActivity(settingsActivity);
            });
        if (BuildConfig.DEBUG) {
            // Crude tests of spliceInPackageChanges()
            bottomSheetHelper
                .addItem(R.drawable.ic_more_vert_white_48dp, R.string.splice_test, () -> {
                    List<ApplicationIconHideable> apps = AppInfoCache
                        .get()
                        .getAppDrawerActivities();
                    String tested = "";
                    int i;
                    for (i = 0; i < 100; i++) {
                        int target = (int) (Math.random() * apps.size());
                        ApplicationIconHideable sample = apps.get(target);
                        List<ApplicationIconHideable> toAdd = apps.stream()
                            .filter(app -> app.getPackageName().equals(sample.getPackageName()))
                            .collect(Collectors.toList());
                        tested += sample.getPackageName() + "\n";
                        mAdapter.spliceInPackageChanges(
                            sample.getPackageName(),
                            toAdd);
                        if (!mAdapter.isConsistent_USE_FOR_DEBUGGING_ONLY()) {
                            tested = tested.trim();
                            Toast.makeText(mContext, "Failed after: \n" + tested, Toast.LENGTH_LONG).show();
                            ClipboardManager cm = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("", tested));
                            break;
                        }
                    }
                    if (i == 100) {
                        Toast
                            .makeText(mContext,
                                      "Passed 100 iteration splice test.",
                                      Toast.LENGTH_SHORT)
                            .show();
                    }
                });
            bottomSheetHelper.addItem(R.drawable.ic_reorder_white_48dp, R.string.run_preset_splice_test, () -> {
                ClipboardManager cm = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                String clipContents = String.valueOf(cm.getPrimaryClip().getItemAt(0).getText());
                String[] pkgs = clipContents.split("\n");
                for (int i = 0; i < pkgs.length; i++) {
                    if (i == pkgs.length - 1) {
                        Log.d("AppDrawerController", "Set a breakpoint here");
                    }
                    mAdapter.spliceInPackageChanges(
                        pkgs[i], AppInfoCache.get().getActivitiesForPackage(pkgs[i]));
                }
            });
            bottomSheetHelper.addItem(R.drawable.ic_visibility_white_48dp, R.string.show_new_user_bottom_sheet, () -> {
                new NewUserBottomSheet(mContext).show();
            });
        }
        bottomSheetHelper.show(mContext, mContext.getString(R.string.settings));
    }

    public void showHideAppsMenu() {
        HiddenAppsBottomSheet.show(mContext, apps -> {
            DatabaseEditor.get().saveHiddenAppsFromIcons(apps);
            AppInfoCache.get().reloadVisibleActivities();
            reloadAppList();
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPackageModifiedEvent(PackageModifiedEvent event) {
        refetchAppIconsForPackage(event.getPackageName());
    }

    private void refetchAppIconsForPackage(String changedPackage) {
        if (mIsSearching) {
            quitSearch();
        }
        if (mAdapter == null) {
            return;
        }
        final List<ApplicationIconHideable> newApps =
            AppInfoCache.get().getActivitiesForPackage(changedPackage);
        mAdapter.spliceInPackageChanges(changedPackage, newApps);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPackageBulkModifiedEvent(PackagesBulkModifiedEvent event) {
        reloadAppList();
    }

    @OnTextChanged(value = R.id.search_box, callback = OnTextChanged.Callback.TEXT_CHANGED)
    public void onSearchChanged(CharSequence s, int start, int before, int count) {
        storeSearchButton.setVisibility(mAdapter.performSearch(s.toString()) ? GONE : VISIBLE);
        mLayoutManager.setReverseLayout(true);
    }

    @OnEditorAction(R.id.search_box)
    public boolean onSearchAction(int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_GO ||
            (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN)) {
            return launchFirstApp();
        }
        return false;
    }

    private boolean launchFirstApp() {
        if (mAdapter.getItemCount() < 1) {
            return false;
        }
        View child = appRecyclerView.getChildAt(0);
        ApplicationIcon app = mAdapter.getFirstApp();
        InstalledAppUtils.launchApp(child, app.getPackageName(), app.getActivityName());
        return true;
    }

    @OnClick(R.id.store_search_button)
    public void searchMarketClicked() {
        final String text = searchBox.getText().toString();
        final String uri = "market://search?q=" + Uri.encode(text);
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(uri));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            ViewUtils.activityOf(mContext).startActivity(intent);
        } catch (ActivityNotFoundException activityNotFoundException) {
            Toast.makeText(mContext, R.string.store_not_installed, Toast.LENGTH_SHORT).show();
        }
        quitSearch();
    }

    public View getView() {
        return rootView;
    }

    public void hideApp(ApplicationIcon ai) {
        mAdapter.hideApp(ai);
    }

    public boolean isSearching() {
        return mIsSearching;
    }

    public boolean isAlphabeticalPickerOpen() {
        return mScrollController.getInFastScroll();
    }

    @Override
    public void onFastScrollStateChange(boolean isEntering) {
        appRecyclerView.animate().alpha(isEntering ? 0F : 1F).setDuration(200).start();
        appRecyclerView.animate().scaleX(isEntering ? 0.9F : 1F).setDuration(200).start();
        appRecyclerView.animate().scaleY(isEntering ? 0.9F : 1F).setDuration(200).start();
    }

    @NonNull
    @Override
    public Map<String, Integer> getHeaderToCountMap() {
        return mAdapter.getHeaderToCountMap();
    }

    @Override
    public void scrollToLetter(char letter) {
        mAdapter.scrollToLetter(letter);
        appRecyclerView.post(() -> DecorViewManager.get(mContext).detachTopView());
    }

    @NonNull
    @Override
    public Context getHostContext() {
        return mContext;
    }

    @Override
    public int hostWidth() {
        return appRecyclerView.getWidth();
    }

    private int approxItemCountOnScreen() {
        int itemHeight = mContext.getResources().getDimensionPixelSize(R.dimen.app_icon_row_height);
        return (int) Math.floor(appRecyclerView.getHeight() / itemHeight);
    }

    public void feedKeyboardEvent(KeyEvent event) {
        if (isAlphabeticalPickerOpen()) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                scrollToLetter((char) event.getUnicodeChar(0));
            }
            return;
        }

        if (!mIsSearching) {
            enterSearch();
        }
        searchBox.requestFocus();
        searchBox.onKeyDown(event.getKeyCode(), event);
    }

    public void feedTrackballEvent(MotionEvent event) {
        appRecyclerView.onTouchEvent(event);
    }

    public interface Host {

        void editFolderOrder();
    }
}
