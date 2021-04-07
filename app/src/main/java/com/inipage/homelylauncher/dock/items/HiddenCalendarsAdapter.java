package com.inipage.homelylauncher.dock.items;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.CalendarUtils;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HiddenCalendarsAdapter extends RecyclerView.Adapter<HiddenCalendarsAdapter.ApplicationVH> {

    private final List<CalendarUtils.Calendar> mData;
    private final Callback mCallback;

    public HiddenCalendarsAdapter(List<CalendarUtils.Calendar> objects, Callback callback) {
        mData = objects;
        mCallback = callback;
    }

    @NotNull
    @Override
    public ApplicationVH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ApplicationVH(
            LayoutInflater.from(parent.getContext()).inflate(
                R.layout.text_row, parent, false));
    }

    @Override
    public void onBindViewHolder(ApplicationVH holder, int position) {
        final CalendarUtils.Calendar calendar = mData.get(position);
        holder.title.setText(calendar.getDisplayName());
        holder.title.setPaintFlags(
            (!calendar.isEnabled() ? Paint.STRIKE_THRU_TEXT_FLAG : 0) | Paint.ANTI_ALIAS_FLAG);
        holder.title.setAlpha(calendar.isEnabled() ? 1.0F : 0.5F);
        holder.itemView.setOnClickListener(v -> {
            calendar.setEnabled(!calendar.isEnabled());
            onBindViewHolder(holder, position);
            mCallback.onCalendarsUpdated(mData);
        });
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    interface Callback {
        void onCalendarsUpdated(List<CalendarUtils.Calendar> modifiedList);
    }

    public static class ApplicationVH extends RecyclerView.ViewHolder {

        TextView title;

        public ApplicationVH(View itemView) {
            super(itemView);
            title = (TextView) itemView;
        }
    }
}