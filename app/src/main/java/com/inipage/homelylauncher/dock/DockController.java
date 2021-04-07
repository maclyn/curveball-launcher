package com.inipage.homelylauncher.dock;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
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

public class DockController {

    private static final int MAX_ITEM_COUNT = 10;

    private final LinearLayout mContainer;
    private final ContextualAppFetcher mAppFetcher;

    private Map<Integer, DockItem> mAppBackedItemsCache;

    public DockController(LinearLayout container) {
        mContainer = container;
        mAppFetcher = new ContextualAppFetcher();
        mAppBackedItemsCache = new HashMap<>();
        mContainer.post(this::reloadDock);
    }

    public void reloadDock() {
        mAppFetcher.reloadPrefs();
        mAppBackedItemsCache.clear();
        mAppBackedItemsCache = DatabaseEditor.get().getDockItems()
            .parallelStream()
            .filter(dockItem -> dockItem.getWhenToShow() !=
                DockItem.DOCK_SHOW_NEVER)
            .collect(Collectors.toConcurrentMap(
                DockItem::getWhenToShow,
                Function.identity()));
        refreshDockItems();
    }

    public void refreshDockItems() {
        mContainer.removeAllViews();
        final List<DockControllerItem> items = getActiveItems();
        final Context context = mContainer.getContext();
        mContainer.addView(ViewUtils.createFillerWidthView(
            context, (int) context.getResources().getDimension(R.dimen.home_activity_margin)));
        for (int i = 0; i < Math.min(items.size(), MAX_ITEM_COUNT); i++) {
            final LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            final DockControllerItem item = items.get(i);
            final View view =
                LayoutInflater.from(context).inflate(
                    R.layout.dock_collapsed_item, mContainer, false);

            // Setup background color
            final View dockItemContainer = view.findViewById(R.id.dock_item_root_container);
            dockItemContainer.getBackground().setColorFilter(
                new PorterDuffColorFilter(
                    item.getTint(context, color ->
                        dockItemContainer.getBackground()
                            .setColorFilter(new PorterDuffColorFilter(
                                color,
                                PorterDuff.Mode.SRC_IN))),
                    PorterDuff.Mode.SRC_IN));

            // Map icon
            if (item.getIcon() != 0) {
                ((ImageView) view.findViewById(R.id.contextual_dock_item_icon)).setImageResource(
                    item.getIcon());
            } else {
                ((ImageView) view.findViewById(R.id.contextual_dock_item_icon)).setImageBitmap(
                    item.getBitmap(context));
            }

            // Map text
            @Nullable final String label = item.getLabel(context);
            if (label != null) {
                ((TextView) view.findViewById(R.id.contextual_dock_item_label)).setText(label);
            } else {
                ((View) view.findViewById(R.id.contextual_dock_item_label).getParent())
                    .setVisibility(GONE);
            }
            @Nullable final String secondaryLabel = item.getSecondaryLabel(context);
            if (secondaryLabel != null) {
                ((TextView) view.findViewById(R.id.contextual_dock_item_secondary_label)).setText(
                    secondaryLabel);
            } else {
                view.findViewById(R.id.contextual_dock_item_secondary_label).setVisibility(GONE);
            }

            // Map actions
            view.setOnClickListener(v -> item.getAction(v, context).run());
            view.setOnLongClickListener(v -> {
                @Nullable final Runnable action = item.getSecondaryAction(v, context);
                if (action != null) {
                    action.run();
                }
                return action != null;
            });

            mContainer.addView(view, layoutParams);
        }
        mContainer.addView(ViewUtils.createFillerWidthView(
            context, (int) context.getResources().getDimension(R.dimen.home_activity_margin)));
    }

    private List<DockControllerItem> getActiveItems() {
        final Context context = mContainer.getContext();
        final List<DockControllerItem> items = new ArrayList<>();
        items.add(new AlarmMappedDockItem(context));
        items.add(new CalendarMappedDockItem(context));
        items.add(new PhoneMappedDockItem(context, mAppBackedItemsCache));
        items.add(new PowerMappedDockItem(context, mAppBackedItemsCache));
        items.addAll(mAppFetcher.getRecentApps(context));
        return items.parallelStream()
            .filter(dockControllerItem -> dockControllerItem.isActive(context))
            .sorted((left, right) -> {
                if (left.getBasePriority() != right.getBasePriority()) {
                    return (int) (right.getBasePriority() - left.getBasePriority());
                }
                return (int) (right.getSubPriority() - left.getSubPriority());
            }).collect(Collectors.toList());
    }
}
