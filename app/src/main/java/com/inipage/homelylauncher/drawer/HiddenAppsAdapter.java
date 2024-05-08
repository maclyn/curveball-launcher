package com.inipage.homelylauncher.drawer;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.model.ApplicationIconHideable;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HiddenAppsAdapter extends RecyclerView.Adapter<HiddenAppsAdapter.ApplicationVH> {

    private final List<ApplicationIconHideable> mData;
    private final Callback mCallback;

    public HiddenAppsAdapter(List<ApplicationIconHideable> objects, Callback callback) {
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
        final ApplicationIconHideable icon = mData.get(position);
        holder.title.setText(icon.getName());
        holder.title.setPaintFlags(
            (icon.isHidden() ? Paint.STRIKE_THRU_TEXT_FLAG : 0) | Paint.ANTI_ALIAS_FLAG);
        holder.icon.setBitmap(
            IconCacheSync.getInstance(
                holder.icon.getContext()).getActivityIcon(
                icon.getPackageName(), icon.getActivityName()));
        holder.icon.setAlpha(icon.isHidden() ? 0.5F : 1.0F);
        holder.itemView.setOnClickListener(v -> {
            icon.setHidden(!icon.isHidden());
            onBindViewHolder(holder, position);
            mCallback.onAppsUpdated(mData);
        });
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public interface Callback {
        void onAppsUpdated(List<ApplicationIconHideable> modifiedList);
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
