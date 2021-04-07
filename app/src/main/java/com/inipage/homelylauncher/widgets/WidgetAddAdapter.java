package com.inipage.homelylauncher.widgets;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WidgetAddAdapter extends RecyclerView.Adapter<WidgetAddAdapter.WidgetAddVH> {

    private final Context mContext;
    private final List<WidgetProviderWrapper> mObjects;
    private OnWidgetClickListener mListener;

    public WidgetAddAdapter(List<WidgetProviderWrapper> objects, Context context) {
        this.mObjects = objects;
        this.mContext = context;
    }

    @NotNull
    @Override
    public WidgetAddVH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new WidgetAddVH(
            LayoutInflater.from(parent.getContext())
                .inflate(R.layout.widget_preview, parent, false));
    }

    @Override
    public void onBindViewHolder(@NotNull WidgetAddVH holder, int position) {
        final WidgetProviderWrapper providerWrapper = mObjects.get(position);
        if (providerWrapper == null) {
            return;
        }

        holder.widgetPreview.setImageDrawable(null);
        holder.widgetPreview.setTag(providerWrapper.appWidgetProviderInfo);
        setImageAsync(providerWrapper.appWidgetProviderInfo, holder.widgetPreview);
        holder.mainLayout.setOnClickListener(view -> {
            if (mListener != null) {
                mListener.onClick(providerWrapper.appWidgetProviderInfo);
            }
        });

        final String appName = providerWrapper.appName;
        final String providerName = providerWrapper.title;
        if (TextUtils.isEmpty(providerName) || providerName.equals(appName)) {
            holder.widgetName.setText(appName);
            holder.appName.setVisibility(View.GONE);
        } else {
            holder.widgetName.setText(providerName);
            holder.appName.setText(appName);
            holder.appName.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return mObjects.size();
    }

    @SuppressLint("StaticFieldLeak")
    private void setImageAsync(final AppWidgetProviderInfo awpi, final ImageView iv) {
        new AsyncTask<PackageManager, Void, Drawable>() {
            @Override
            protected Drawable doInBackground(PackageManager... params) {
                Drawable preview;
                try {
                    if (awpi.previewImage != 0) {
                        preview = params[0].getDrawable(
                            awpi.provider.getPackageName(),
                            awpi.previewImage,
                            null);
                    } else {
                        preview = params[0].getApplicationIcon(awpi.provider.getPackageName());
                    }
                } catch (Exception e) {
                    preview = ContextCompat.getDrawable(
                        iv.getContext(), android.R.drawable.sym_def_app_icon);
                }
                return preview;
            }

            @Override
            protected void onPostExecute(Drawable d) {
                AppWidgetProviderInfo tag = (AppWidgetProviderInfo) iv.getTag();
                if (tag != null && tag.equals(awpi) && d != null) {
                    iv.setImageDrawable(d);
                }
            }
        }.execute(mContext.getPackageManager());
    }

    public void setOnClickListener(OnWidgetClickListener listener) {
        this.mListener = listener;
    }

    public interface OnWidgetClickListener {
        void onClick(AppWidgetProviderInfo awpi);
    }

    // lol yikes

    public static class WidgetProviderWrapper {

        public AppWidgetProviderInfo appWidgetProviderInfo;
        public String title;
        public String appName;

        public WidgetProviderWrapper(Context context, AppWidgetProviderInfo awpi) {
            appWidgetProviderInfo = awpi;
            title = awpi.loadLabel(context.getPackageManager());
            try {
                appName = context
                    .getPackageManager()
                    .getApplicationInfo(awpi.provider.getPackageName(), 0)
                    .loadLabel(context.getPackageManager())
                    .toString();
            } catch (PackageManager.NameNotFoundException ignored) {
                appName = title;
            }
        }
    }

    public static class WidgetAddVH extends RecyclerView.ViewHolder {
        private final LinearLayout mainLayout;
        private final TextView widgetName;
        private final TextView appName;
        private final ImageView widgetPreview;

        public WidgetAddVH(View itemView) {
            super(itemView);
            mainLayout = (LinearLayout) itemView;
            widgetName = mainLayout.findViewById(R.id.widget_preview_text);
            appName = mainLayout.findViewById(R.id.widget_app_name);
            widgetPreview = mainLayout.findViewById(R.id.widget_preview_image);
        }
    }
}
