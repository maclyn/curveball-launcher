package com.inipage.homelylauncher.swipefolders;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.model.SwipeFolder;
import com.inipage.homelylauncher.views.BottomSheetHelper;

import java.util.List;

public class ReorderFolderBottomSheet {

    public static void show(Context context, List<SwipeFolder> folders, Callback callback) {
        final View contentView =
            LayoutInflater.from(context).inflate(R.layout.reorder_item_container_view, null, false);
        final ReorderController<SwipeFolder> reorderController =
            new ReorderController<>(
                folders,
                contentView.findViewById(R.id.reorder_container_layout),
                (item, icon, label) -> {
                    icon.setImageBitmap(
                        IconCacheSync
                            .getInstance(context)
                            .getNamedResource(item.getDrawablePackage(), item.getDrawableName()));
                    label.setText(item.getTitle());
                });
        new BottomSheetHelper()
            .addActionItem(R.string.save, () ->
                callback.onFoldersReordered(reorderController.getReorderedList()))
            .setContentView(contentView)
            .show(context, context.getString(R.string.reorder_rows));
    }

    public interface Callback {
        void onFoldersReordered(List<SwipeFolder> reorderedFolders);
    }
}
