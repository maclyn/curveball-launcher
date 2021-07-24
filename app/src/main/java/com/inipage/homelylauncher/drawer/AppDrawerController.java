package com.inipage.homelylauncher.drawer;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.ParseException;
import android.net.Uri;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.SettingsActivity;
import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.caches.PackageModifiedEvent;
import com.inipage.homelylauncher.caches.PackagesBulkModifiedEvent;
import com.inipage.homelylauncher.grid.AppViewHolder;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.pager.BasePageController;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.utils.InstalledAppUtils;
import com.inipage.homelylauncher.utils.Prewarmer;
import com.inipage.homelylauncher.utils.ViewUtils;
import com.inipage.homelylauncher.views.BottomSheetHelper;
import com.inipage.homelylauncher.views.DecorViewDragger;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
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
public class AppDrawerController implements BasePageController {

    private final Host mHost;
    private final Context mContext;
    private final Drawable mQuitDrawable;
    private final Drawable mSearchDrawable;
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
    @BindView(R.id.search_box_button)
    ImageView searchBoxButton;
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

    @SuppressLint("ClickableViewAccessibility")
    public AppDrawerController(Host host, ViewGroup rootView) {
        mHost = host;
        mContext = rootView.getContext();

        final LayoutInflater inflater = LayoutInflater.from(mContext);
        final View appDrawerView = inflater.inflate(R.layout.app_drawer_view, rootView, false);
        ButterKnife.bind(this, appDrawerView);

        DrawableCompat.setTint(
            searchBox.getBackground(),
            ContextCompat.getColor(mContext, R.color.primary_text_color));
        mQuitDrawable = ContextCompat.getDrawable(mContext, R.drawable.ic_clear_white_48dp);
        mSearchDrawable = ContextCompat.getDrawable(mContext, R.drawable.ic_search_white_48dp);
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
                    if (!(holder instanceof ApplicationIconAdapter.AlphaAwareViewHolder)) {
                        continue;
                    }
                    ApplicationIconAdapter.AlphaAwareViewHolder iconHolder =
                        (ApplicationIconAdapter.AlphaAwareViewHolder) holder;
                    if (idx == firstItem && firstItem != firstCompletelyVisibleItem ||
                        idx == lastItem && lastItem != lastCompletelyVisibleItem) {
                        iconHolder.applyAlpha(recyclerView);
                    } else {
                        iconHolder.view.setAlpha(1F);
                    }
                }
            }
        });
        reloadAppList();
    }

    public void reloadAppList() {
        if (mIsSearching) {
            quitSearch();
        }
        mAdapter = new ApplicationIconAdapter(ViewUtils.activityOf(mContext));
        mAdapter.setHasStableIds(true);
        mLayoutManager.setReverseLayout(mIsSearching);
        appRecyclerView.setAdapter(mAdapter);
    }

    public void quitSearch() {
        if (!mIsSearching || mInDrag) {
            return;
        }
        searchBox.setText("");
        searchBox.clearFocus();
        hideKeyboard();
        mIsSearching = false;
        searchBoxButton.setImageDrawable(mSearchDrawable);
        searchBox.setVisibility(GONE);
        storeSearchButton.setVisibility(GONE);
        mAdapter.leaveSearch();
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

    @OnClick(R.id.search_box_button)
    public void onSearchButtonClicked(View view) {
        if (!mIsSearching) {
            enterSearch();
        } else {
            quitSearch();
        }
    }

    private void enterSearch() {
        if (mIsSearching) {
            return;
        }

        mIsSearching = true;
        searchBoxButton.setImageDrawable(mQuitDrawable);
        searchBox.setVisibility(VISIBLE);
        searchBox.requestFocus();
        showKeyboard();
    }

    private void showKeyboard() {
        @Nullable InputMethodManager imm =
            (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @OnClick(R.id.bottom_sheet_settings_button)
    public void onSettingsButtonClicked(View view) {
        new BottomSheetHelper()
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
            })
            .show(mContext, mContext.getString(R.string.settings));
    }

    public void showHideAppsMenu() {
        HiddenAppsBottomSheet.show(mContext, apps -> {
            DatabaseEditor.get().saveHiddenAppsFromIcons(apps);
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

    public interface Host {

        void editFolderOrder();
    }
}
