package com.inipage.homelylauncher.folders;

import android.content.Context;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.AppLabelCache;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.model.GridFolder;
import com.inipage.homelylauncher.model.GridFolderApp;
import com.inipage.homelylauncher.views.BottomSheetHelper;

import java.util.List;

public class FolderEditingBottomSheet {
    public interface Callback {

        void onFolderSaved(List<GridFolderApp> reorderedApps);

        void onFolderDeleted();
    }

    public static void show(
        Context context, GridFolder folder, boolean isNew, Callback callback
    ) {
        final View contentView =
            LayoutInflater.from(context).inflate(R.layout.folder_editing_view, null, false);

        // Icon reordering
        final ReorderItemsController<GridFolderApp> reorderItemsController =
            new ReorderItemsController<>(
                folder.getApps(),
                contentView.findViewById(R.id.reorder_container_layout),
                (item, icon, label) -> {
                    icon.setImageBitmap(
                        IconCacheSync.getInstance(context).getActivityIcon(
                            item.getPackageName(), item.getActivityName()));
                    label.setText(AppLabelCache.getInstance(context).getLabel(
                        item.getPackageName(), item.getActivityName()));
                });

        final String bottomSheetTitle =
            isNew ?
                context.getString(R.string.new_folder_bottom_sheet_title) :
                context.getString(R.string.edit_folder_bottom_sheet_title);
        final BottomSheetHelper helper = new BottomSheetHelper().setContentView(contentView);
        if (isNew) {
            helper.addActionItem(R.string.delete, callback::onFolderDeleted);
        }
        helper.addActionItem(
            R.string.save, () -> callback.onFolderSaved(reorderItemsController.getReorderedList()));
        helper.show(context, bottomSheetTitle);
    }
}
