package com.inipage.homelylauncher.icons;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.views.BottomSheetHelper;
import com.inipage.homelylauncher.views.DecorViewManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class IconPickerBottomSheet {

    private final ExecutorService mIconFetchExecutor = Executors.newSingleThreadExecutor();
    private final List<String> mIconPackLabels = new ArrayList<>();
    private final List<String> mIconPackPackages = new ArrayList<>();
    private final Callback mCallback;
    private final RecyclerView mListView;
    private final EditText mSearchBox;
    private final Spinner mSourceSpinner;
    private final String mAttachmentToken;

    @Nullable
    private List<Pair<String, Integer>> mIconsResourceAndId;
    @Nullable
    private Resources mActiveResources;
    @Nullable
    private Runnable mIconFetchRunnable;

    public IconPickerBottomSheet(Context context, Callback callback) {
        mCallback = callback;
        final View contentView =
            LayoutInflater.from(context).inflate(R.layout.icon_chooser_view, null, false);
        mListView = contentView.findViewById(R.id.icon_preview_list);
        mSourceSpinner = contentView.findViewById(R.id.icon_source_spinner);
        mSearchBox = contentView.findViewById(R.id.icon_search_bar);

        // Source list
        populateIconPacksList(context);
        final ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
            context, android.R.layout.simple_spinner_item, mIconPackLabels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSourceSpinner.setAdapter(spinnerAdapter);
        mSourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                fetchIconsInPackage(context, mIconPackPackages.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Icon search box
        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchIcons(context, s.toString());
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
                .show(context, context.getString(R.string.pick_an_icon));
    }

    private void populateIconPacksList(Context context) {
        // Add ourselves
        mIconPackPackages.add(context.getPackageName());
        mIconPackLabels.add(context.getString(R.string.included_icons));

        // Add matches
        // com.novalauncher.THEME
        final Intent allPacksIntent = new Intent("com.gau.go.launcherex.theme", null);
        List<ResolveInfo> matches =
            context.getPackageManager().queryIntentActivities(allPacksIntent, 0);
        for (ResolveInfo ri : matches) {
            mIconPackPackages.add(ri.activityInfo.packageName);
            mIconPackLabels.add((String) ri.loadLabel(context.getPackageManager()));
        }
    }

    private void fetchIconsInPackage(Context context, String packageName) {
        mSearchBox.setText("");
        mSearchBox.setEnabled(false);
        mIconFetchRunnable = () -> {
            try {
                final Resources resources =
                    context.getPackageManager().getResourcesForApplication(packageName);
                final int drawableRes = resources.getIdentifier("drawable", "xml", packageName);
                XmlPullParser xmlParser = null;
                if (drawableRes != 0) {
                    xmlParser = resources.getXml(drawableRes);
                } else {
                    try {
                        final AssetManager am = resources.getAssets();
                        final String[] assets = am.list("");
                        for (String str : assets) {
                            if (!str.equals("drawable.xml")) {
                                continue;
                            }

                            try {
                                xmlParser = am.openXmlResourceParser(str);
                            } catch (Exception defaultParserUnavailable) {
                                try {
                                    final InputStream inputStream = am.open(str);
                                    final XmlPullParserFactory factory =
                                        XmlPullParserFactory.newInstance();
                                    factory.setNamespaceAware(true);
                                    xmlParser = factory.newPullParser();
                                    xmlParser.setInput(inputStream, null);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    } catch (Exception e) {
                        throw new Exception("No drawable.xml file found");
                    }
                }

                // Go through and grab drawable="" components
                List<Pair<String, Integer>> iconData = new ArrayList<>();
                int eventType =
                    xmlParser == null ?
                    XmlPullParser.END_DOCUMENT :
                    xmlParser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType != XmlPullParser.START_TAG) {
                        eventType = xmlParser.next();
                        continue;
                    }

                    final String attrValue = xmlParser.getAttributeValue(null, "drawable");
                    if (attrValue != null) {
                        final int resId = resources.getIdentifier(
                            attrValue,
                            "drawable",
                            packageName);
                        if (resId != 0) {
                            String name = resources.getResourceEntryName(resId);
                            iconData.add(new Pair<>(name, resId));
                        }
                    }
                    eventType = xmlParser.next();
                }
                if (iconData.isEmpty()) {
                    throw new Exception();
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    mIconsResourceAndId = iconData;
                    mActiveResources = resources;
                    mSearchBox.setEnabled(true);
                    mIconFetchRunnable = null;
                    setAdapter(context, iconData);
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

    private void searchIcons(Context context, String query) {
        if (mIconsResourceAndId == null || mIconsResourceAndId.isEmpty()) {
            return;
        }

        if (query.length() > 0) {
            final String cleanedQuery = query.toLowerCase(Locale.US).replace(" ", "_");
            final List<Pair<String, Integer>> iconResults =
                mIconsResourceAndId
                    .parallelStream()
                    .filter(icon -> icon.first.contains(cleanedQuery))
                    .collect(Collectors.toList());
            setAdapter(context, iconResults);
        } else {
            setAdapter(context, mIconsResourceAndId);
        }
    }

    private void setAdapter(Context context, List<Pair<String, Integer>> iconData) {
        final RecyclerView.Adapter<IconChooserAdapter.IconHolder> iconListAdapter =
            new IconChooserAdapter(
                iconData,
                mActiveResources,
                iconResource -> {
                    final String packageName =
                        mIconPackPackages.get(mSourceSpinner.getSelectedItemPosition());
                    mCallback.onIconPicked(packageName, iconResource.first);
                    DecorViewManager.get(context).removeView(mAttachmentToken);
                });
        mListView.setAdapter(iconListAdapter);
    }

    public interface Callback {

        void onIconPicked(String iconPackage, String iconDrawable);
    }
}
