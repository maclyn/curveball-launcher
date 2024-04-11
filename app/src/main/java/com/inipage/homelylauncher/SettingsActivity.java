package com.inipage.homelylauncher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.dock.ActivityPickerBottomSheet;
import com.inipage.homelylauncher.dock.HiddenRecentAppsBottomSheet;
import com.inipage.homelylauncher.dock.items.HiddenCalendarsPickerBottomSheet;
import com.inipage.homelylauncher.icons.IconPackLoader;
import com.inipage.homelylauncher.icons.IconPickerBottomSheet;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.persistence.DatabaseHelper;
import com.inipage.homelylauncher.persistence.PrefsHelper;
import com.inipage.homelylauncher.utils.Constants;
import com.inipage.homelylauncher.utils.FileUtils;
import com.inipage.homelylauncher.utils.LifecycleLogUtils;
import com.inipage.homelylauncher.utils.ViewUtils;
import com.inipage.homelylauncher.views.ProvidesOverallDimensions;
import com.jakewharton.processphoenix.ProcessPhoenix;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class SettingsActivity extends AppCompatActivity implements ProvidesOverallDimensions {

    private static final int EXPORT_DATABASE_REQUEST_CODE = 1001;
    private static final int IMPORT_DATABASE_REQUEST_CODE = 1002;
    private static final int EXPORT_LOG_REQUEST_CODE = 1003;
    private static final SimpleDateFormat DATABASE_TITLE_FORMAT =
        new SimpleDateFormat("hhmma_MM_dd_yyyy", Locale.US);

    interface PrefRunnable {
        void run(Context context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager()
            .beginTransaction()
            .replace(android.R.id.content, new MainFragment())
            .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            Toast.makeText(
                this,
                "Failed.",
                Toast.LENGTH_SHORT)
                .show();
            return;
        }
        switch (requestCode) {
            case EXPORT_DATABASE_REQUEST_CODE:
            case EXPORT_LOG_REQUEST_CODE:
                try {
                    final ParcelFileDescriptor fd =
                        getContentResolver().openFileDescriptor(data.getData(), "rw");
                    final FileInputStream fis = new FileInputStream(
                        requestCode == EXPORT_DATABASE_REQUEST_CODE ?
                        DatabaseEditor.get().getPath() :
                        LifecycleLogUtils.getLogfilePath(this));
                    final FileOutputStream fos = new FileOutputStream(fd.getFileDescriptor());
                    final byte[] chunk = new byte[1024];
                    while (fis.read(chunk) != -1) {
                        fos.write(chunk);
                    }
                    fis.close();
                    fos.close();
                    fd.close();
                    Toast.makeText(this, "File exported.", Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                }
                break;
            case IMPORT_DATABASE_REQUEST_CODE:
                try {
                    final ParcelFileDescriptor fd =
                        getContentResolver().openFileDescriptor(data.getData(), "r");
                    final FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
                    final String tempFile = new File(getFilesDir(), "/tmp.db").toString();
                    final FileOutputStream verificationFos = new FileOutputStream(tempFile);
                    byte[] chunk = new byte[1024];
                    while (fis.read(chunk) != -1) {
                        verificationFos.write(chunk);
                    }
                    fis.close();
                    verificationFos.close();
                    fd.close();

                    // Check if the database file is actually readable by us
                    try {
                        final DatabaseHelper testHelper = new DatabaseHelper(this, tempFile);
                        @Nullable final String problem = testHelper.findProblemsWithDatabase();
                        if (problem != null) {
                            throw new Exception(problem);
                        }
                    } catch (Exception cantRead) {
                        Toast.makeText(
                            this,
                            cantRead.getMessage(),
                            Toast.LENGTH_SHORT)
                            .show();
                        return;
                    }
                    FileUtils.copy(tempFile, DatabaseEditor.get().getPath());
                    ProcessPhoenix.triggerRebirth(this);
                } catch (Exception ignored) {
                    Toast.makeText(
                        this,
                        "Couldn't import this database.",
                        Toast.LENGTH_SHORT)
                        .show();
                }
                break;
        }
    }

    @Override
    public Pair<Integer, Integer> provideScrims() {
        View view = getWindow().getDecorView();
        return new Pair<>(view.getRootWindowInsets().getStableInsetTop(), view.getRootWindowInsets().getStableInsetBottom());
    }

    @Override
    public Rect provideOverallBounds() {
        View view = getWindow().getDecorView();
        return new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
    }

    public static class MainFragment extends PreferenceFragment {

        private String mMissingIconPackage = "unset";
        private final List<ApplicationIconHideable> mMissingIcons = new LinkedList<>();
        private int mMissingIconsIdx = 0;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);

            setupAdvancedPrefs();
            setupIconPackPrefs();

            // Dock
            bindCheckboxPreference("use_g_weather", Constants.USE_G_WEATHER_PREF, false, null);
            bindPreference("manage_cals", ctx -> HiddenCalendarsPickerBottomSheet.show(ctx, null));
            bindPreference("manage_hidden_apps",
               HiddenRecentAppsBottomSheet.INSTANCE::showHiddenRecentAppsBottomSheet);
            bindCheckboxPreference("celcius_pref", Constants.WEATHER_USE_CELCIUS_PREF);

            bindPreference("import_database", context -> {
                final Activity parent = ViewUtils.requireActivityOf(context);
                final Intent exportIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                exportIntent.setType("*/*");
                exportIntent.addCategory(Intent.CATEGORY_OPENABLE);
                parent.startActivityForResult(exportIntent, IMPORT_DATABASE_REQUEST_CODE);
            });
            bindPreference("export_database", context -> {
                final Activity parent = ViewUtils.requireActivityOf(context);
                final Intent exportIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                exportIntent.setType("*/*");
                exportIntent.addCategory(Intent.CATEGORY_OPENABLE);
                exportIntent.putExtra(
                    Intent.EXTRA_TITLE,
                    DATABASE_TITLE_FORMAT.format(new Date()) +
                        "_curveball_launcher_backup.db");
                parent.startActivityForResult(exportIntent, EXPORT_DATABASE_REQUEST_CODE);
            });


            // Attributions
            bindPreference("attrs", context ->
                new AlertDialog.Builder(context)
                    .setTitle(R.string.attributions)
                    .setMessage(R.string.attributions_message)
                    .setNegativeButton(R.string.close, null)
                    .show());
        }

        private void bindPreference(String name, PrefRunnable action) {
            findPreference(name).setOnPreferenceClickListener(pref -> {
                action.run(pref.getContext());
                return true;
            });
        }

        private void bindCheckboxPreference(String name, String backingPreference) {
            bindCheckboxPreference(name, backingPreference, false, null);
        }

        private void bindCheckboxPreference(
                String name,
                String backingPreference,
                boolean triggerRestart,
                @Nullable PrefRunnable listener) {
            Preference pref = findPreference(name);
            if (!(pref instanceof CheckBoxPreference)) {
                return;
            }
            CheckBoxPreference checkBoxPreference = (CheckBoxPreference) pref;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            checkBoxPreference.setChecked(prefs.getBoolean(backingPreference, false));
            checkBoxPreference.setOnPreferenceClickListener(preference -> {
                if (!(preference instanceof CheckBoxPreference)) {
                    return false;
                }
                prefs.edit().putBoolean(
                    backingPreference, ((CheckBoxPreference) preference).isChecked()).commit();
                if (listener != null) {
                    listener.run(getContext());
                }
                if (triggerRestart) {
                    ProcessPhoenix.triggerRebirth(preference.getContext());
                }
                return true;
            });
        }

        private void showLogs(Context context) {
            ScrollView wrapper = new ScrollView(context);
            TextView view = new TextView(context);
            view.setText(LifecycleLogUtils.dumpLog());
            view.setTextSize(context.getResources().getDimensionPixelSize(R.dimen.log_text_size));
            view.setTextColor(Color.WHITE);
            view.setTypeface(Typeface.MONOSPACE);
            wrapper.addView(view);

            new AlertDialog.Builder(context)
                .setTitle(R.string.show_debug_log)
                .setView(wrapper)
                .setPositiveButton(R.string.export_log, (dialog, which) -> {
                    ClipboardManager cm =
                        (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText(
                        "Export log",
                        LifecycleLogUtils.dumpLog()));
                    Toast.makeText(getActivity(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                }).show();
        }

        private void exportLogs(Context context) {
            final Activity parent = ViewUtils.requireActivityOf(context);
            final Intent exportIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            exportIntent.setType("*/*");
            exportIntent.addCategory(Intent.CATEGORY_OPENABLE);
            exportIntent.putExtra(
                Intent.EXTRA_TITLE,
                DATABASE_TITLE_FORMAT.format(new Date()) + "_logfile.txt");
            parent.startActivityForResult(exportIntent, EXPORT_LOG_REQUEST_CODE);
        }

        private void setupAdvancedPrefs() {
            final boolean isDevModeEnabled = PrefsHelper.isDevMode();
            bindCheckboxPreference(
                "dev_mode", Constants.DEV_MODE_PREF, false, context -> setupAdvancedPrefs());
            bindPreference("log_show", this::showLogs);
            bindPreference("log_export", this::exportLogs);
            bindPreference("log_clear", __ -> LifecycleLogUtils.clearLog());
            /*
            // TODO: Horribly breaks app right now; need to figure this out
            bindCheckboxPreference(
                "vertical_scroller_design", Constants.VERTICAL_SCROLLER_PREF, true, null);
             */

            // Dangerous DB options
            bindPreference("reset_database", context -> {
                DatabaseEditor.get().dropAllTables();
                ProcessPhoenix.triggerRebirth(context);
            });
            bindPreference("move_database_to_b", context -> {
                FileUtils.copy(
                    DatabaseEditor.get().getPath(),
                    getBPath(context));
                Toast.makeText(context, "Database moved.", Toast.LENGTH_SHORT).show();
            });
            bindPreference("overwrite_from_b", context -> {
                final String bPath = getBPath(context);
                if (!(new File(bPath).exists())) {
                    Toast.makeText(context, "No database in B slot.", Toast.LENGTH_SHORT).show();
                    return;
                }
                FileUtils.copy(
                    bPath,
                    DatabaseEditor.get().getPath());
                ProcessPhoenix.triggerRebirth(context);
            });

            findPreference("vertical_scroller_design").setEnabled(isDevModeEnabled);
            findPreference("reset_database").setEnabled(isDevModeEnabled);
            findPreference("move_database_to_b").setEnabled(isDevModeEnabled);
            findPreference("overwrite_from_b").setEnabled(isDevModeEnabled);
        }

        private void setupIconPackPrefs() {
            // Main icon pack
            final String defaultPack = "unset";
            final String currentPack = PrefsHelper.getIconPack();
            ListPreference iconPackPref = (ListPreference) findPreference("icon_packs");
            List<Pair<String, String>> iconPacks = IconPackLoader.Companion.resolveIconPacks(getContext());
            Pair<String, String> defaultOption = Pair.create(defaultPack, "Default");
            CharSequence[] iconPackNames =
                Stream.concat(Stream.of(defaultOption), iconPacks.stream())
                    .map(stringStringPair -> stringStringPair.second)
                    .toArray(CharSequence[]::new);
            CharSequence[] iconPackValues =
                Stream.concat(Stream.of(defaultOption), iconPacks.stream())
                    .map(stringStringPair -> stringStringPair.first)
                    .toArray(CharSequence[]::new);
            int selectedIndex = 0;
            for (int i = 0; i < iconPackValues.length; i++) {
                if (iconPackValues[i].equals(currentPack)) {
                    selectedIndex = i;
                    break;
                }
            }
            iconPackPref.setValueIndex(selectedIndex);
            iconPackPref.setEntryValues(iconPackValues);
            iconPackPref.setEntries(iconPackNames);
            iconPackPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue.equals(defaultPack)) {
                    PrefsHelper.setIconPack(null);
                } else {
                    PrefsHelper.setIconPack((String) newValue);
                }
                restartHomeActivity();
                return true;
            });

            Preference fillIconsPref = findPreference("fill_missing_icons");
            fillIconsPref.setEnabled(currentPack != null && !currentPack.equals(defaultPack));
            fillIconsPref.setOnPreferenceClickListener(preference -> {
                runMissingIconReplacement();
                return true;
            });

            Preference replaceOnePref = findPreference("replace_one_icon");
            replaceOnePref.setEnabled(currentPack != null && !currentPack.equals(defaultPack));
            replaceOnePref.setOnPreferenceClickListener(preference -> {
                runSingleIconReplacement();
                return true;
            });

            Preference clearStandIns = findPreference("clear_icon_replacements");
            clearStandIns.setEnabled(currentPack != null && !currentPack.equals(defaultPack));
            clearStandIns.setOnPreferenceClickListener(preference -> {
                PrefsHelper.clearStandIns();
                restartHomeActivity();
                return true;
            });
        }

        private void restartHomeActivity() {
            IconCacheSync.getInstance(getContext()).clearCache();
            setupIconPackPrefs();
            getContext().sendBroadcast(new Intent(Constants.INTENT_ACTION_RESTART));
        }

        private void runMissingIconReplacement() {
            @Nullable String iconPack = PrefsHelper.getIconPack();
            if (iconPack == null) {
                return;
            }
            mMissingIconPackage = iconPack;
            mMissingIcons.clear();
            mMissingIconsIdx = 0;
            IconPackLoader ipl =
                IconCacheSync.getInstance(getContext()).getIconPackLoader(iconPack);
            List<ApplicationIconHideable> apps = AppInfoCache.get().getAllActivities();
            for (ApplicationIconHideable app : apps) {
                if (!ipl.hasIconForComponent(app.getPackageName(), app.getActivityName())) {
                    mMissingIcons.add(app);
                }
            }
            Toast.makeText(getContext(), mMissingIcons.size() + " missing icon(s) found", Toast.LENGTH_SHORT).show();
            runMissingIconReplacementAtCurrentIdx();
        }

        private void runMissingIconReplacementAtCurrentIdx() {
            if (mMissingIconsIdx >= mMissingIcons.size()) {
                restartHomeActivity();
                return;
            }
            ApplicationIconHideable app = mMissingIcons.get(mMissingIconsIdx);
            new IconPickerBottomSheet(getContext(), (iconPackage, iconDrawable) -> {
                replaceIconWithDrawable(app.getPackageName(),
                                        app.getActivityName(),
                                        iconDrawable);
                mMissingIconsIdx++;
                runMissingIconReplacementAtCurrentIdx();
            }, mMissingIconPackage, app.getName() + " replacement icon");
        }

        private void runSingleIconReplacement() {
            new ActivityPickerBottomSheet(getContext(), (packageName, activityName) -> {
                mMissingIconPackage = PrefsHelper.getIconPack();
                if (mMissingIconPackage == null) {
                    return;
                }
                mMissingIcons.clear();
                mMissingIcons.add(
                    new ApplicationIconHideable(getContext(), packageName, activityName, false));
                mMissingIconsIdx = 0;
                runMissingIconReplacementAtCurrentIdx();
            }, "Select an app for icon replacement");
        }

        private void replaceIconWithDrawable(String packageName, String activity, String drawable) {
            @Nullable String iconPack = PrefsHelper.getIconPack();
            if (iconPack == null) {
                return;
            }
            PrefsHelper.setStandIn(iconPack, Pair.create(packageName, activity), drawable);
        }

        private String getBPath(Context context) {
            return new File(context.getFilesDir(), "b.db").toString();
        }
    }
}
