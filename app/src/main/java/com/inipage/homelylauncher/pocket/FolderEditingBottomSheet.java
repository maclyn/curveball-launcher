package com.inipage.homelylauncher.pocket;

import android.content.Context;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.icons.IconPickerBottomSheet;
import com.inipage.homelylauncher.model.SwipeApp;
import com.inipage.homelylauncher.model.SwipeFolder;
import com.inipage.homelylauncher.views.BottomSheetHelper;

import java.util.List;

public class FolderEditingBottomSheet {

    public static void show(
        Context context, SwipeFolder folder, boolean isNew, Callback callback) {
        final View contentView =
            LayoutInflater.from(context).inflate(R.layout.folder_editing_view, null, false);

        // Icon reordering
        final ReorderController<SwipeApp> reorderController =
            new ReorderController<>(
                folder.getShortcutApps(),
                contentView.findViewById(R.id.reorder_container_layout),
                (item, icon, label) -> {
                    icon.setImageBitmap(item.getIcon(context));
                    label.setText(item.getLabel(context));
                });

        // Title
        final EditText labelEntry = contentView.findViewById(R.id.folder_label);
        labelEntry.setText(folder.getTitle());

        // Icon
        final ImageButton icon = contentView.findViewById(R.id.folder_icon_button);
        Pair<String, String> iconRef =
            new Pair<>(folder.getDrawablePackage(), folder.getDrawableName());
        icon.setImageBitmap(
            IconCacheSync.getInstance(context).getNamedResource(iconRef.first, iconRef.second));
        icon.setTag(iconRef);
        icon.setOnClickListener(v -> {
            new IconPickerBottomSheet(context, (iconPackage, iconDrawable) -> {
                icon.setImageBitmap(
                    IconCacheSync.getInstance(context).getNamedResource(iconPackage, iconDrawable));
                icon.setTag(new Pair<>(iconPackage, iconDrawable));
            });
        });

        final String bottomSheetTitle = isNew ?
                                        context.getString(R.string.new_folder_bottom_sheet_title) :
                                        context.getString(R.string.edit_folder_bottom_sheet_title);
        final BottomSheetHelper helper = new BottomSheetHelper()
            .setContentView(contentView);
        if (isNew) {
            helper.addActionItem(R.string.delete, callback::onFolderDeleted);
        }
        helper.addActionItem(R.string.save, () -> {
            final Pair<String, String> iconTag = (Pair<String, String>) icon.getTag();
            callback.onFolderSaved(
                labelEntry.getText().toString().isEmpty() ?
                context.getString(R.string.default_folder_title) :
                labelEntry.getText().toString(),
                iconTag.first,
                iconTag.second,
                reorderController.getReorderedList());
        });
        helper.show(context, bottomSheetTitle);
    }

    public interface Callback {

        void onFolderSaved(
            String title,
            String iconPackage,
            String iconDrawable,
            List<SwipeApp> reorderedApps);

        void onFolderDeleted();
    }
}
