package com.inipage.homelylauncher.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class LifecycleLogUtils {

    private static final String TAG = "LifecycleLogUtils";
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    // 256KB
    private static final long FILE_TOO_BIG_TO_DISPLAY_BYTES = (long) (0.25 * 1024 * 1024);
    @SuppressLint("ConstantLocale")
    private static final SimpleDateFormat DATE_FORMATTER =
        new SimpleDateFormat("hh:mm:ss:SS aa (EEE, d MMM yyyy)", Locale.getDefault());

    private static String SESSION_ID;
    private static File LOG_FILE;
    private static FileInputStream LOG_FILE_INPUT_STREAM;
    private static FileOutputStream LOG_FILE_OUTPUT_STREAM;

    public static String getLogfilePath(Context context) {
        final File filesDir = context.getFilesDir();
        return filesDir + "/logfile.txt";
    }

    public static void openLog(Context context) {
        try {
            SESSION_ID = UUID.randomUUID().toString();
            LOG_FILE = new File(getLogfilePath(context));
            LOG_FILE_OUTPUT_STREAM = new FileOutputStream(LOG_FILE, true);
            LOG_FILE_INPUT_STREAM = new FileInputStream(LOG_FILE);
            logEvent(LogType.LOG, "Session started");
        } catch (Exception fileIoEx) {
            throw new RuntimeException("Log file cannot be opened!");
        }
    }

    /**
     * Append to an internal log of meaningful com.inipage.homelylauncher.state changes.
     *
     * @param level   The log-level. Eventually will allow filtering.
     * @param message A String representation of the event.
     */
    public static void logEvent(LogType level, String message) {
        try {
            final String result = level.name() +
                "|" +
                message.replace("|", "I") +
                "|" +
                new Date() +
                "|" +
                SESSION_ID +
                "\n";
            LOG_FILE_OUTPUT_STREAM.write(result.getBytes(CHARSET));
            LOG_FILE_OUTPUT_STREAM.flush();
            Log.d(TAG, result);
        } catch (Exception ignored) {
        }
    }

    public static void closeLog() {
        try {
            logEvent(LogType.LOG, "Session ended");
            LOG_FILE_OUTPUT_STREAM.close();
        } catch (Exception ignored) {
        }
    }

    public static void clearLog() {
        try {
            LOG_FILE_OUTPUT_STREAM.flush();
            FileChannel channel = LOG_FILE_OUTPUT_STREAM.getChannel();
            channel.truncate(0);
            LOG_FILE_OUTPUT_STREAM.flush();
            SESSION_ID = UUID.randomUUID().toString();
            logEvent(LogType.LOG, "Log cleared");
        } catch (Exception ignored) {
        }
    }

    public static String dumpLog() {
        try {
            long size = LOG_FILE_OUTPUT_STREAM.getChannel().size();
            if (size > FILE_TOO_BIG_TO_DISPLAY_BYTES) {
                return "Long file. Try exporting to read.";
            }
        } catch (Exception ignored) {}

        StringBuilder result = new StringBuilder();
        byte[] buf = new byte[1024];
        int read = 0;
        int off = 0;
        try {
            while ((read = LOG_FILE_INPUT_STREAM.read(buf, off, 1024)) >= 0) {
                result.append(new String(buf, CHARSET));
            }
            LOG_FILE_INPUT_STREAM = new FileInputStream(LOG_FILE);
            return result.toString();
        } catch (Exception e) {
            return "Log cannot be read!";
        }
    }

    public enum LogType {
        LOG,
        LIFECYCLE_CHANGE,
        ERROR
    }
}
