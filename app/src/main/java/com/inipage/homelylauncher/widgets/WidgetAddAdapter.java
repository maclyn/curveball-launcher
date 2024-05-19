package com.inipage.homelylauncher.widgets;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.ViewUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import kotlin.Pair;

public class WidgetAddAdapter extends RecyclerView.Adapter<WidgetAddAdapter.WidgetAddVH> {

    private final Context mContext;
    private final List<WidgetProviderWrapper> mObjects;
    private OnWidgetClickListener mListener;

    public WidgetAddAdapter(
        Context context,
        List<WidgetProviderWrapper> objects
    ) {
        mObjects = objects;
        mContext = context;
    }

    @NotNull
    @Override
    public WidgetAddVH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new WidgetAddVH(
            LayoutInflater.from(parent.getContext())
                .inflate(R.layout.widget_preview, parent, false));
    }

    @SuppressLint("ResourceType")
    @Override
    public void onBindViewHolder(@NotNull WidgetAddVH holder, int position) {
        final WidgetProviderWrapper providerWrapper = mObjects.get(position);
        if (providerWrapper == null) {
            return;
        }
        final AppWidgetProviderInfo awpi = providerWrapper.appWidgetProviderInfo;
        holder.mainLayout.setOnClickListener(view -> {
            if (mListener != null) {
                mListener.onClick(awpi);
            }
        });

        // Preview image or layout
        Pair<Integer, Integer> widthAndHeight =
            WidgetLifecycleUtils.guessDesiredPreviewBounds(mContext, awpi);
        boolean hasPreviewLayout =
            WidgetLifecycleUtils.isMaterialYouCompatible() && awpi.previewLayout != 0;
        View targetView = hasPreviewLayout ? holder.widgetLayoutPreview : holder.widgetPreview;
        ViewUtils.setWidth(targetView, widthAndHeight.getFirst());
        ViewUtils.setHeight(targetView, widthAndHeight.getSecond());
        holder.widgetPreview.setVisibility(hasPreviewLayout ? View.GONE : View.VISIBLE);
        holder.widgetLayoutPreview.setVisibility(hasPreviewLayout ? View.VISIBLE : View.GONE);
        if (hasPreviewLayout) {
            holder.widgetLayoutPreview.removeAllViews();
            holder.widgetLayoutPreview.setTag(awpi);
            setLayoutPreviewIntoViewAsync(awpi, holder.widgetLayoutPreview);
        } else {
            holder.widgetPreview.setImageDrawable(null);
            holder.widgetPreview.setTag(awpi);
            setImageAsync(awpi, holder.widgetPreview);
        }

        final String appName = providerWrapper.appName;
        final String providerName = providerWrapper.title;
        if (TextUtils.isEmpty(providerName)) {
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

    @SuppressLint("StaticFieldLeak")
    private void setLayoutPreviewIntoViewAsync(
        final AppWidgetProviderInfo awpi,
        final FrameLayout container)
    {
        new AsyncTask<Void, Void, View>() {
            @SuppressLint("ResourceType")
            @RequiresApi(api = Build.VERSION_CODES.S)
            @Override
            protected View doInBackground(Void... params) {
                @Nullable Context widgetAppContext = null;
                try {
                    widgetAppContext =
                        mContext.createPackageContext(awpi.provider.getPackageName(), 0);
                } catch (PackageManager.NameNotFoundException ignored) {
                    return null;
                }
                try {
                    return LayoutInflater
                         .from(widgetAppContext)
                         .inflate(awpi.previewLayout, null, false);
                } catch (Resources.NotFoundException ignored) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(@Nullable View view) {
                AppWidgetProviderInfo tag = (AppWidgetProviderInfo) container.getTag();
                if (tag != null && tag.equals(awpi) && view != null) {
                    FrameLayout.LayoutParams layoutParams =
                        new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                    container.addView(view, layoutParams);
                }
            }
        }.execute();
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
        private final FrameLayout widgetLayoutPreview;

        public WidgetAddVH(View itemView) {
            super(itemView);
            mainLayout = (LinearLayout) itemView;
            widgetName = mainLayout.findViewById(R.id.widget_preview_text);
            appName = mainLayout.findViewById(R.id.widget_app_name);
            widgetPreview = mainLayout.findViewById(R.id.widget_preview_image);
            widgetLayoutPreview = mainLayout.findViewById(R.id.widget_preview_layout);
        }
    }
}
