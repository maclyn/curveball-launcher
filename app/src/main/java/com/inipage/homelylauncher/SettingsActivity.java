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
import android.net.Uri;
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
import com.inipage.homelylauncher.caches.FontCacheSync;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SettingsActivity extends AppCompatActivity implements ProvidesOverallDimensions {

    private static final int EXPORT_DATABASE_REQUEST_CODE = 1001;
    private static final int IMPORT_DATABASE_REQUEST_CODE = 1002;
    private static final int EXPORT_LOG_REQUEST_CODE = 1003;
    private static final int EXPORT_SHARED_PREFS_REQUEST_CODE = 1004;
    private static final int IMPORT_SETTINGS_REQUEST_CODE = 1005;
    private static final int IMPORT_GRID_FONT_REQUEST_CODE = 1006;
    private static final int IMPORT_APP_LIST_FONT_REQUEST_CODE = 1007;
    private static final int IMPORT_DOCK_FONT_REQUEST_CODE = 1008;
    private static final int EXPORT_FULL_REQUEST_CODE = 1009;
    private static final int IMPORT_FULL_REQUEST_CODE = 1010;

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
        if (resultCode != Activity.RESULT_OK || data == null) {
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
            case EXPORT_SHARED_PREFS_REQUEST_CODE:
                try {
                    final ParcelFileDescriptor fd =
                        getContentResolver().openFileDescriptor(data.getData(), "rw");
                    String path = null;
                    switch (requestCode) {
                        case EXPORT_DATABASE_REQUEST_CODE:
                            path = DatabaseEditor.get().getPath();
                            break;
                        case EXPORT_LOG_REQUEST_CODE:
                            path = LifecycleLogUtils.getLogfilePath(this);
                            break;
                        case EXPORT_SHARED_PREFS_REQUEST_CODE:
                            path = PrefsHelper.getSharedPrefsPath(this);
                            break;
                    }
                    final FileOutputStream fos = new FileOutputStream(fd.getFileDescriptor());
                    copyFromPathToOutputStream(path, fos);
                    fos.close();
                    fd.close();
                    Toast.makeText(this, "File exported.", Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                }
                break;
            case EXPORT_FULL_REQUEST_CODE:
                try {
                    final ParcelFileDescriptor fd =
                        getContentResolver().openFileDescriptor(data.getData(), "rw");
                    final FileOutputStream fos = new FileOutputStream(fd.getFileDescriptor());
                    final ZipOutputStream zipOut = new ZipOutputStream(fos);

                    // SharedPrefs
                    zipOut.putNextEntry(new ZipEntry("settings.xml"));
                    copyFromPathToOutputStream(PrefsHelper.getSharedPrefsPath(this), zipOut);
                    zipOut.closeEntry();
                    // Fonts
                    File fontOverrides = new File(getFilesDir(), Constants.FONT_OVERRIDES_PATH);
                    if (fontOverrides.exists()) {
                        @Nullable File[] contents = fontOverrides.listFiles();
                        if (contents != null) {
                            // These are copied with their individual names
                            for (File fontOverride : contents) {
                                String fontOverrideName = fontOverride.getName();
                                zipOut.putNextEntry(new ZipEntry(fontOverrideName));
                                copyFromPathToOutputStream(fontOverride.toString(), zipOut);
                                zipOut.closeEntry();
                            }
                        }
                    }
                    // DB
                    zipOut.putNextEntry(new ZipEntry("database.db"));
                    copyFromPathToOutputStream(DatabaseEditor.get().getPath(), zipOut);
                    zipOut.closeEntry();

                    zipOut.close();
                    Toast.makeText(this, "All settings exported.", Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {

                }
                break;
            case IMPORT_FULL_REQUEST_CODE:
                final String tmpZipFileName = "tmp.zip";
                if (handleImportActivityResult(this, data, tmpZipFileName)) {
                    try {
                        File tmpZipFile = new File(getFilesDir(), tmpZipFileName);
                        ZipInputStream zis = new ZipInputStream(new FileInputStream(tmpZipFile));
                        @Nullable ZipEntry zipEntry;
                        while ((zipEntry = zis.getNextEntry()) != null) {
                            String entryName = zipEntry.getName();
                            switch (entryName) {
                                case "database.db":
                                    FileUtils.copyFromInputStreamToPath(
                                        zis, DatabaseEditor.get().getPath());
                                    break;
                                case "settings.xml":
                                    FileUtils.copyFromInputStreamToPath(
                                        zis,
                                        FileUtils.filesDirPath(
                                            this, Constants.SHARED_PREFS_IMPORT_PATH));
                                    break;
                                case Constants.GRID_FONT_PATH:
                                case Constants.LIST_FONT_PATH:
                                case Constants.DOCK_FONT_PATH:
                                    File fontOverrides = new File(getFilesDir(), Constants.FONT_OVERRIDES_PATH);
                                    if (!fontOverrides.exists()) {
                                        fontOverrides.mkdir();
                                    }
                                    Path fontDst =
                                        Paths.get(getFilesDir().toString(),
                                                  Constants.FONT_OVERRIDES_PATH,
                                                  entryName);
                                    FileUtils.copyFromInputStreamToPath(zis, fontDst.toString());
                                    break;
                            }
                        }
                        zis.close();
                        tmpZipFile.delete();
                    } catch (Exception ignored) {}
                }
                ProcessPhoenix.triggerRebirth(this);
                break;
            case IMPORT_SETTINGS_REQUEST_CODE: {
                if (handleImportActivityResult(this, data, Constants.SHARED_PREFS_IMPORT_PATH)) {
                    // Overwrite will happen in Application create
                    ProcessPhoenix.triggerRebirth(this);
                } else {
                    Toast.makeText(
                        this, "Couldn't import this settings file.", Toast.LENGTH_SHORT).show();
                }
            }
            case IMPORT_DATABASE_REQUEST_CODE:
                final String tmpDbFileName = "tmp.db";
                if (handleImportActivityResult(this, data, tmpDbFileName)) {
                    File tmpDbFile = new File(getFilesDir(), tmpDbFileName);
                    // Check if the database file is actually readable by us
                    try (final DatabaseHelper testHelper =
                             new DatabaseHelper(this, tmpDbFile.getPath())) {
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
                    FileUtils.copy(tmpDbFile.getPath(), DatabaseEditor.get().getPath());
                    tmpDbFile.delete();
                    ProcessPhoenix.triggerRebirth(this);
                } else {
                    Toast.makeText(
                        this, "Couldn't import this database file.", Toast.LENGTH_SHORT).show();
                }
                break;
            case IMPORT_GRID_FONT_REQUEST_CODE:
            case IMPORT_APP_LIST_FONT_REQUEST_CODE:
            case IMPORT_DOCK_FONT_REQUEST_CODE:
                final String tmpFontFile = "tmp_font.ttf";
                try {
                    if (!handleImportActivityResult(this, data, tmpFontFile)) {
                        throw new Exception();
                    }
                    File fontFile = new File(getFilesDir(), tmpFontFile);
                    Typeface.createFromFile(fontFile);

                    String fontSubPath = "";
                    switch (requestCode) {
                        case IMPORT_GRID_FONT_REQUEST_CODE:
                            fontSubPath = Constants.GRID_FONT_PATH;
                            break;
                        case IMPORT_APP_LIST_FONT_REQUEST_CODE:
                            fontSubPath = Constants.LIST_FONT_PATH;
                            break;
                        case IMPORT_DOCK_FONT_REQUEST_CODE:
                            fontSubPath = Constants.DOCK_FONT_PATH;
                            break;
                    }
                    File fontOverrides = new File(getFilesDir(), Constants.FONT_OVERRIDES_PATH);
                    if (!fontOverrides.exists()) {
                        fontOverrides.mkdir();
                    }
                    File fontDstPath = new File(fontOverrides, fontSubPath);
                    if (fontDstPath.exists()) {
                        fontDstPath.delete();
                    }
                    FileUtils.copy(fontFile.toString(), fontDstPath.toString());
                    fontFile.delete();
                    FontCacheSync.Companion.get().clear();
                    restartHomeActivity();
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to open font file.", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    public Pair<Integer, Integer> provideVerticalScrims() {
        View view = getWindow().getDecorView();
        return new Pair<>(view.getRootWindowInsets().getStableInsetTop(), view.getRootWindowInsets().getStableInsetBottom());
    }

    @Override
    public Pair<Integer, Integer> provideScrimYPositionsOnScreen() {
        return Pair.create(0, 0);
    }

    @Override
    public Rect provideOverallBounds() {
        View view = getWindow().getDecorView();
        return new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
    }

    private boolean handleImportActivityResult(Context context, Intent data, String dstFilesPath) {
        try {
            @Nullable Uri dataUri = data.getData();
            if (dataUri == null) {
                return false;
            }
            @Nullable
            final ParcelFileDescriptor fd =
                context.getContentResolver().openFileDescriptor(dataUri, "r");
            if (fd == null) {
                return false;
            }
            final FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
            final String tempFile = new File(context.getFilesDir(), dstFilesPath).toString();
            final FileOutputStream tmpFileFos = new FileOutputStream(tempFile);
            byte[] chunk = new byte[1024];
            while (fis.read(chunk) != -1) {
                tmpFileFos.write(chunk);
            }
            fis.close();
            tmpFileFos.close();
            fd.close();
        } catch (IOException ignored) {
            return false;
        }
        return true;
    }

    /**
     * Callers MUST close the output stream themselves.
     */
    private void copyFromPathToOutputStream(String path, OutputStream os) {
        try {
            final FileInputStream fis = new FileInputStream(path);
            final byte[] chunk = new byte[1024];
            while (fis.read(chunk) != -1) {
                os.write(chunk);
            }
            fis.close();
        } catch (Exception ignored) {}
    }


    private void restartHomeActivity() {
        sendBroadcast(new Intent(Constants.INTENT_ACTION_RESTART));
    }

    public static class MainFragment extends PreferenceFragment {

        private String mMissingIconPackage = "unset";
        private final List<ApplicationIconHideable> mMissingIcons = new LinkedList<>();
        private int mMissingIconsIdx = 0;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);

            setupAppearancePrefs();
            setupAdvancedPrefs();
            setupIconPackPrefs();

            // Dock
            bindCheckboxPreference("mono_dock", Constants.MONOCHROME_DOCK_PREF);
            bindPreference("manage_cals", ctx -> HiddenCalendarsPickerBottomSheet.show(ctx, null));
            bindPreference("manage_hidden_apps",
               HiddenRecentAppsBottomSheet.INSTANCE::showHiddenRecentAppsBottomSheet);

            // Backups
            bindPreference("full_backup", context -> {
                launchExportIntent(context, "_curveball_full_backup.zip", EXPORT_FULL_REQUEST_CODE);
            });
            bindPreference("full_restore", context -> {
                launchImportIntent(context, IMPORT_FULL_REQUEST_CODE);
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

        private void setupAppearancePrefs() {
            bindPreference("import_grid_font", context -> launchImportIntent(context, IMPORT_GRID_FONT_REQUEST_CODE));
            bindPreference("import_list_font", context -> launchImportIntent(context, IMPORT_APP_LIST_FONT_REQUEST_CODE));
            bindPreference("import_dock_font", context -> launchImportIntent(context, IMPORT_DOCK_FONT_REQUEST_CODE));

            bindPreference("reset_font_overrides", context -> {
                File fontOverrides = new File(context.getFilesDir(), Constants.FONT_OVERRIDES_PATH);
                boolean deleted = false;
                if (fontOverrides.exists()) {
                    @Nullable File[] contents = fontOverrides.listFiles();
                    if (contents != null) {
                        for (File fontOverride : contents) {
                            fontOverride.delete();
                        }
                        if (fontOverrides.delete()) {
                            deleted = true;
                        }
                    }
                }
                if (deleted) {
                    FontCacheSync.Companion.get().clear();
                    getContext().sendBroadcast(new Intent(Constants.INTENT_ACTION_RESTART));
                }
                Toast.makeText(
                    context,
                    deleted ?
                        "Font overrides removed." :
                        "No font overrides removed.",
                    Toast.LENGTH_SHORT).show();
            });
        }

        private void setupAdvancedPrefs() {
            final boolean isDevModeEnabled = PrefsHelper.isDevMode();
            bindCheckboxPreference(
                "dev_mode", Constants.DEV_MODE_PREF, false, context -> setupAdvancedPrefs());
            bindPreference("log_show", this::showLogs);
            bindPreference("log_export", context -> launchExportIntent(context, "_logfile.txt", EXPORT_LOG_REQUEST_CODE));
            bindPreference("log_clear", __ -> LifecycleLogUtils.clearLog());

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
            bindPreference("import_database", context -> {
                launchImportIntent(context, IMPORT_DATABASE_REQUEST_CODE);
            });
            bindPreference("export_database", context -> {
                launchExportIntent(context, "_curveball_launcher_backup.db", EXPORT_DATABASE_REQUEST_CODE);
            });
            bindPreference("import_settings", context -> {
                launchImportIntent(context, IMPORT_SETTINGS_REQUEST_CODE);
            });
            bindPreference("export_settings", context -> {
                launchExportIntent(context, "_curveball_settings.xml", EXPORT_SHARED_PREFS_REQUEST_CODE);
            });

            findPreference("import_database").setEnabled(isDevModeEnabled);
            findPreference("export_database").setEnabled(isDevModeEnabled);
            findPreference("import_settings").setEnabled(isDevModeEnabled);
            findPreference("export_settings").setEnabled(isDevModeEnabled);
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

        private void launchExportIntent(Context context, String filePostfix, int requestCode) {
            final Activity parent = ViewUtils.requireActivityOf(context);
            final Intent exportIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            exportIntent.setType("*/*");
            exportIntent.addCategory(Intent.CATEGORY_OPENABLE);
            exportIntent.putExtra(
                Intent.EXTRA_TITLE,
                DATABASE_TITLE_FORMAT.format(new Date()) + filePostfix);
            parent.startActivityForResult(exportIntent, requestCode);
        }

        private void launchImportIntent(Context context, int requestCode) {
            final Activity parent = ViewUtils.requireActivityOf(context);
            final Intent exportIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            exportIntent.setType("*/*");
            exportIntent.addCategory(Intent.CATEGORY_OPENABLE);
            parent.startActivityForResult(exportIntent, requestCode);
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
                if (!ipl.probablyHasIconForComponent(app.getPackageName(), app.getActivityName())) {
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
