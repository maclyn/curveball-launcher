package com.inipage.homelylauncher.icons;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class IconChooserAdapter extends RecyclerView.Adapter<IconChooserAdapter.IconHolder> {

    public interface Callback {
        void onIconSelected(String drawableName);
    }

    private final IconPackLoader mIpl;
    private final Callback mCallback;
    private List<String> mIconPackDrawables;

    public IconChooserAdapter(
        IconPackLoader ipl,
        Callback callback)
    {
        mIpl = ipl;
        mIconPackDrawables = mIpl.getIconPackDrawables();
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
        String drawableName = mIconPackDrawables.get(i);
        Drawable d = mIpl.loadDrawableByName(drawableName);
        viewHolder.icon.setImageDrawable(d);
        viewHolder.icon.setOnClickListener(v -> mCallback.onIconSelected(drawableName));
    }

    @Override
    public int getItemCount() {
        return mIconPackDrawables.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void filter(String query) {
        if (!query.isEmpty()) {
            final String cleanedQuery = query.toLowerCase(Locale.US).replace(" ", "_");
            mIconPackDrawables =
                mIpl.getIconPackDrawables()
                    .parallelStream()
                    .filter(drawableName -> drawableName.contains(cleanedQuery))
                    .collect(Collectors.toList());
        } else {
            mIconPackDrawables = mIpl.getIconPackDrawables();
        }
        notifyDataSetChanged();
    }

    public static class IconHolder extends RecyclerView.ViewHolder {

        ImageView icon;

        public IconHolder(ImageView mainView) {
            super(mainView);
            this.icon = mainView;
        }
    }
}
