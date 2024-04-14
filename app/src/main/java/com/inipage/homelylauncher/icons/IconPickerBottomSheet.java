package com.inipage.homelylauncher.icons;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.util.Pair;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.views.BottomSheetHelper;
import com.inipage.homelylauncher.views.DecorViewManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IconPickerBottomSheet {

    private final ExecutorService mIconFetchExecutor = Executors.newSingleThreadExecutor();
    private final List<String> mIconPackLabels = new ArrayList<>();
    private final List<String> mIconPackPackages = new ArrayList<>();
    private final Callback mCallback;
    private final RecyclerView mListView;
    private final EditText mSearchBox;
    private final Spinner mSourceSpinner;
    private final String mAttachmentToken;

    @Nullable private IconChooserAdapter mAdapter;

    @Nullable
    private Runnable mIconFetchRunnable;
    @Nullable private String mFixedPackage;

    public IconPickerBottomSheet(Context context, Callback callback, @Nullable String fixedPackage, @Nullable String title) {
        mCallback = callback;
        final View contentView =
            LayoutInflater.from(context).inflate(R.layout.icon_chooser_view, null, false);
        mListView = contentView.findViewById(R.id.icon_preview_list);
        mSourceSpinner = contentView.findViewById(R.id.icon_source_spinner);
        mSearchBox = contentView.findViewById(R.id.icon_search_bar);

        // Source list, if needed
        if (fixedPackage != null) {
            mIconPackPackages.add(fixedPackage);
            mFixedPackage = fixedPackage;
            mSourceSpinner.setVisibility(View.GONE);
        } else {
            populateIconPacksList(context);
            final ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, mIconPackLabels);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mSourceSpinner.setAdapter(spinnerAdapter);
            mSourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id) {
                    fetchIconsInPackage(context, mIconPackPackages.get(position));
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        // Icon search box
        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchIcons(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Icon view adapter
        mListView.setLayoutManager(
            new GridLayoutManager(context, 5, LinearLayoutManager.VERTICAL, false));
        fetchIconsInPackage(context, mIconPackPackages.get(0));

        // Show the bottom sheet
        mAttachmentToken =
            new BottomSheetHelper()
                .setContentView(contentView)
                .setIsFixedHeight()
                .show(context, title == null ? context.getString(R.string.pick_an_icon) : title);
    }

    private void populateIconPacksList(Context context) {
        // Add ourselves
        mIconPackPackages.add(context.getPackageName());
        mIconPackLabels.add(context.getString(R.string.included_icons));
        // Add installed icons
        for (Pair<String, String> pair : IconPackLoader.Companion.resolveIconPacks(context)) {
            mIconPackPackages.add(pair.first);
            mIconPackLabels.add(pair.second);
        }
    }

    private void fetchIconsInPackage(Context context, String packageName) {
        mSearchBox.setText("");
        mSearchBox.setEnabled(false);
        mIconFetchRunnable = () -> {
            try {
                IconPackLoader ipl =
                    IconCacheSync.getInstance(context).getIconPackLoader(packageName);
                if (ipl.getIconPackDrawables().isEmpty()) {
                    throw new Exception();
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    mSearchBox.setEnabled(true);
                    mIconFetchRunnable = null;
                    setAdapter(context, ipl);
                });
            } catch (Exception fetchFailed) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.failed_to_fetch_icons, packageName),
                        Toast.LENGTH_LONG).show();
                });
            }
        };
        mIconFetchExecutor.execute(mIconFetchRunnable);
    }

    private void searchIcons(String query) {
        if (mAdapter == null) {
            return;
        }
        mAdapter.filter(query);
    }

    private void setAdapter(Context context, IconPackLoader iconPackLoader) {
        final IconChooserAdapter iconListAdapter =
            new IconChooserAdapter(
                iconPackLoader,
                iconResource -> {
                    final String packageName =
                        mFixedPackage == null ?
                        mIconPackPackages.get(mSourceSpinner.getSelectedItemPosition()) :
                        mFixedPackage;
                    mCallback.onIconPicked(packageName, iconResource);
                    DecorViewManager.get(context).removeView(mAttachmentToken);
                });
        mAdapter = iconListAdapter;
        mListView.setAdapter(iconListAdapter);
    }

    public interface Callback {

        void onIconPicked(String iconPackage, String iconDrawable);
    }
}
