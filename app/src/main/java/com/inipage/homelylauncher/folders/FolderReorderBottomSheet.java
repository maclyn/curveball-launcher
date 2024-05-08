package com.inipage.homelylauncher.folders;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.AppLabelCache;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.model.GridFolderApp;
import com.inipage.homelylauncher.views.BottomSheetHelper;

import java.util.List;

public class FolderReorderBottomSheet {

    public static void show(Context context, List<GridFolderApp> folders, Callback callback) {
        final View contentView =
            LayoutInflater.from(context).inflate(R.layout.reorder_item_container_view, null, false);
        final ReorderItemsController<GridFolderApp> reorderItemsController =
            new ReorderItemsController<>(
                folders,
                contentView.findViewById(R.id.reorder_container_layout),
                (item, icon, label) -> {
                    icon.setImageBitmap(
                        IconCacheSync
                            .getInstance(context)
                            .getActivityIcon(item.getPackageName(), item.getActivityName()));
                    label.setText(AppLabelCache.getInstance(context).getLabel(
                        item.getPackageName(), item.getActivityName()));
                });
        new BottomSheetHelper()
            .addActionItem(R.string.save, () ->
                callback.onFolderReordered(reorderItemsController.getReorderedList()))
            .setContentView(contentView)
            .show(context, context.getString(R.string.reorder_rows));
    }

    public interface Callback {
        void onFolderReordered(List<GridFolderApp> reorderedFolders);
    }
}
