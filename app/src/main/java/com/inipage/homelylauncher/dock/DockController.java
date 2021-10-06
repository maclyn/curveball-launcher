package com.inipage.homelylauncher.dock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.dock.items.AlarmMappedDockItem;
import com.inipage.homelylauncher.dock.items.CalendarMappedDockItem;
import com.inipage.homelylauncher.dock.items.ContextualAppFetcher;
import com.inipage.homelylauncher.dock.items.PhoneMappedDockItem;
import com.inipage.homelylauncher.dock.items.PowerMappedDockItem;
import com.inipage.homelylauncher.dock.items.WeatherDockItem;
import com.inipage.homelylauncher.model.DockItem;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.utils.ViewUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Manages dock items at the bottom of the home screen.
 */
public class DockController {

    private static final int MAX_ITEM_COUNT = 20;

    private final LinearLayout mContainer;
    private final ContextualAppFetcher mAppFetcher;

    private List<DockControllerItem> mActiveDockItems;
    private Map<Integer, DockItem> mAppBackedItemsCache;

    public DockController(LinearLayout container) {
        mContainer = container;
        mAppFetcher = new ContextualAppFetcher();
        mAppBackedItemsCache = new HashMap<>();
    }

    public synchronized void loadDock() {
        destroyDockImpl();

        final Context context = mContainer.getContext();
        final boolean isSquarish = ViewUtils.isSquarishDevice(context);

        // Setup the dock controller supporting fields
        mAppFetcher.reloadPrefs();
        mAppBackedItemsCache = DatabaseEditor.get().getDockPreferences()
            .parallelStream()
            .filter(dockItem -> dockItem.getWhenToShow() !=
                DockItem.DOCK_SHOW_NEVER)
            .collect(Collectors.toConcurrentMap(
                DockItem::getWhenToShow,
                Function.identity()));

        // Add all the dock items
        mActiveDockItems = new ArrayList<>();
        mActiveDockItems.add(new AlarmMappedDockItem());
        mActiveDockItems.add(new CalendarMappedDockItem());
        mActiveDockItems.add(new WeatherDockItem());
        mActiveDockItems.add(new PhoneMappedDockItem(mAppBackedItemsCache));
        mActiveDockItems.add(new PowerMappedDockItem(mAppBackedItemsCache));
        mActiveDockItems.addAll(mAppFetcher.getRecentApps(context));
        mActiveDockItems =
            mActiveDockItems
                .parallelStream()
                .sorted((left, right) -> {
                    if (left.getBasePriority() != right.getBasePriority()) {
                        return (int) (right.getBasePriority() - left.getBasePriority());
                    }
                    return (int) (right.getSubPriority() - left.getSubPriority());
                }).collect(Collectors.toList());

        // Bind the dock items to views
        mContainer.addView(ViewUtils.createFillerWidthView(
            context,
            (int) context.getResources().getDimension(
                isSquarish ?
                    R.dimen.home_activity_squarish :
                    R.dimen.home_activity_margin)));
        for (int i = 0; i < Math.min(mActiveDockItems.size(), MAX_ITEM_COUNT); i++) {
            final LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            final View view =
                LayoutInflater.from(context).inflate(
                    R.layout.dock_collapsed_item, mContainer, false);
            view.setVisibility(GONE);
            mContainer.addView(view, layoutParams);

            final DockControllerItem item = mActiveDockItems.get(i);
            item.attach(new DockControllerItem.Host() {
                @Override
                public Context getContext() {
                    return mContainer.getContext();
                }

                @Override
                public void showHostedItem() {
                    bindViewToLoadedItem(isSquarish, view, item);
                    view.setVisibility(VISIBLE);
                }

                @Override
                public void hideHostedItem() {
                    view.setVisibility(GONE);
                }

                @Override
                public void tintLoaded(int color) {
                    final View dockItemContainer = view.findViewById(R.id.dock_item_root_container);
                    dockItemContainer.getBackground().setColorFilter(
                        new PorterDuffColorFilter(
                            color,
                            PorterDuff.Mode.SRC_IN));
                }
            });

        }
        mContainer.addView(ViewUtils.createFillerWidthView(
            context, (int) context.getResources().getDimension(R.dimen.home_activity_margin)));
    }

    public void destroyDock() {
        destroyDockImpl();
    }

    private void destroyDockImpl() {
        mContainer.removeAllViews();
        mAppBackedItemsCache.clear();
        if (mActiveDockItems != null) {
            for (DockControllerItem item : mActiveDockItems) {
                item.detach();
            }
            mActiveDockItems.clear();
            mActiveDockItems = null;
        }
    }

    private static void bindViewToLoadedItem(
            boolean isSquarish,
            View view,
            DockControllerItem item) {
        // Setup background color
        final View dockItemContainer = view.findViewById(R.id.dock_item_root_container);
        dockItemContainer.getBackground().setColorFilter(
            new PorterDuffColorFilter(
                item.getTint(),
                PorterDuff.Mode.SRC_IN));

        // Map icon
        ImageView itemIcon = ((ImageView) view.findViewById(R.id.contextual_dock_item_icon));
        int icon = item.getIcon();
        @Nullable Bitmap bitmap = item.getBitmap();
        @Nullable Drawable drawable = item.getDrawable();
        if (icon != 0) {
            itemIcon.setImageResource(icon);
        } else if (bitmap != null) {
            itemIcon.setImageBitmap(bitmap);
        } else if (drawable != null) {
            itemIcon.setImageDrawable(drawable);
        }

        // Map text
        @Nullable final String label = item.getLabel();
        if (label != null) {
            ((TextView) view.findViewById(R.id.contextual_dock_item_label)).setText(label);
        } else {
            ((View) view.findViewById(R.id.contextual_dock_item_label).getParent())
                .setVisibility(GONE);
        }
        @Nullable final String secondaryLabel = item.getSecondaryLabel();
        if (secondaryLabel != null && !isSquarish) {
            ((TextView) view.findViewById(R.id.contextual_dock_item_secondary_label)).setText(
                secondaryLabel);
        } else {
            view.findViewById(R.id.contextual_dock_item_secondary_label).setVisibility(GONE);
        }

        // Map actions
        view.setOnClickListener(v -> item.getAction(v).run());
        view.setOnLongClickListener(v -> {
            @Nullable final Runnable action = item.getSecondaryAction(v);
            if (action != null) {
                action.run();
            }
            return action != null;
        });
    }
}
