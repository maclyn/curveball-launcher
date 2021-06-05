package com.inipage.homelylauncher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceFragment;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import com.inipage.homelylauncher.dock.items.HiddenCalendarsPickerBottomSheet;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.persistence.DatabaseHelper;
import com.inipage.homelylauncher.utils.FileUtils;
import com.inipage.homelylauncher.utils.LifecycleLogUtils;
import com.inipage.homelylauncher.utils.ViewUtils;
import com.jakewharton.processphoenix.ProcessPhoenix;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final int EXPORT_DATABASE_REQUEST_CODE = 1001;
    private static final int IMPORT_DATABASE_REQUEST_CODE = 1002;
    private static final SimpleDateFormat DATABASE_TITLE_FORMAT =
        new SimpleDateFormat("hhmma_MM_dd_yyyy", Locale.US);

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
                try {
                    final ParcelFileDescriptor fd =
                        getContentResolver().openFileDescriptor(data.getData(), "rw");
                    final FileInputStream fis = new FileInputStream(DatabaseEditor.get().getPath());
                    final FileOutputStream fos = new FileOutputStream(fd.getFileDescriptor());
                    final byte[] chunk = new byte[1024];
                    while (fis.read(chunk) != -1) {
                        fos.write(chunk);
                    }
                    fis.close();
                    fos.close();
                    fd.close();
                    Toast.makeText(
                        this,
                        "Database exported.",
                        Toast.LENGTH_SHORT)
                        .show();
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

    public static class MainFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);

            // Dock
            bindPreference("manage_cals", ctx -> HiddenCalendarsPickerBottomSheet.show(ctx, null));

            // Logging
            bindPreference("log_show", this::showLogs);
            bindPreference("log_clear", __ -> LifecycleLogUtils.clearLog());

            // Database
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
                final Activity parent = ViewUtils.activityOf(context);
                final Intent exportIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                exportIntent.setType("*/*");
                exportIntent.addCategory(Intent.CATEGORY_OPENABLE);
                parent.startActivityForResult(exportIntent, IMPORT_DATABASE_REQUEST_CODE);
            });
            bindPreference("export_database", context -> {
                final Activity parent = ViewUtils.activityOf(context);
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

        private String getBPath(Context context) {
            return new File(context.getFilesDir(), "b.db").toString();
        }

        interface PrefRunnable {
            void run(Context context);
        }
    }
}
