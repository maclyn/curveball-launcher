package com.inipage.homelylauncher.icons;

import android.content.res.Resources;
import android.util.Pair;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IconChooserAdapter extends RecyclerView.Adapter<IconChooserAdapter.IconHolder> {

    private final Callback mCallback;
    private final List<Pair<String, Integer>> mIconList;
    private final Resources mResources;

    public IconChooserAdapter(
        List<Pair<String, Integer>> icons,
        Resources appResources,
        Callback callback) {
        mIconList = icons;
        mResources = appResources;
        mCallback = callback;
    }

    @NotNull
    @Override
    public IconHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        ImageView icon = new ImageView(viewGroup.getContext());
        return new IconHolder(icon);
    }

    //Set up specific customIcon with data
    @Override
    public void onBindViewHolder(IconHolder viewHolder, final int i) {
        viewHolder.icon.setImageDrawable(mResources.getDrawable(mIconList.get(i).second));
        viewHolder.icon.setOnClickListener(v -> mCallback.onIconSelected(mIconList.get(i)));
    }

    @Override
    public int getItemCount() {
        return mIconList.size();
    }

    interface Callback {
        void onIconSelected(Pair<String, Integer> iconResource);
    }

    public static class IconHolder extends RecyclerView.ViewHolder {

        ImageView icon;

        public IconHolder(ImageView mainView) {
            super(mainView);
            this.icon = mainView;
        }
    }
}
