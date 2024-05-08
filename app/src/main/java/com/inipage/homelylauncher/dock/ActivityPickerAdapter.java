package com.inipage.homelylauncher.dock;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.drawer.BitmapView;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.ApplicationIconHideable;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ActivityPickerAdapter extends RecyclerView.Adapter<ActivityPickerAdapter.ApplicationVH> {

    private final List<ApplicationIconHideable> mData;
    private final Callback mCallback;

    public ActivityPickerAdapter(List<ApplicationIconHideable> objects, Callback callback) {
        mData = objects;
        mCallback = callback;
    }

    @NotNull
    @Override
    public ApplicationVH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ApplicationVH(
            LayoutInflater.from(parent.getContext()).inflate(
                R.layout.app_list_row, parent, false));
    }

    @Override
    public void onBindViewHolder(ApplicationVH holder, int position) {
        final ApplicationIcon icon = mData.get(position);
        holder.title.setText(icon.getName());
        holder.icon.setBitmap(
            IconCacheSync.getInstance(
                holder.icon.getContext()).getActivityIcon(
                icon.getPackageName(), icon.getActivityName()));
        holder.itemView.setOnClickListener(v -> mCallback.onActivityPicked(
            icon.getPackageName(),
            icon.getActivityName()));
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    interface Callback {
        void onActivityPicked(String packageName, String activityName);
    }

    public static class ApplicationVH extends RecyclerView.ViewHolder {

        TextView title;
        BitmapView icon;

        public ApplicationVH(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.app_icon_label);
            icon = itemView.findViewById(R.id.app_icon_image);
        }
    }
}
