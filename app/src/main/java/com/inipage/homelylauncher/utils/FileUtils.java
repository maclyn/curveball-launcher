package com.inipage.homelylauncher.utils;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileUtils {

    public static boolean existsInFilesDir(Context context, String path) {
        File f = new File(context.getFilesDir(), path);
        return f.exists();
    }

    public static void deleteFromFilesDir(Context context, String path) {
        File f = new File(context.getFilesDir(), path);
        f.delete();
    }

    public static String filesDirPath(Context context, String path) {
        return new File(context.getFilesDir(), path).getPath();
    }

    public static void copy(String srcPath, String dstPath) {
        try {
            FileInputStream fis = new FileInputStream(srcPath);
            FileOutputStream fos = new FileOutputStream(dstPath, false);
            byte[] chunk = new byte[1024];
            int read = 0;
            while ((read = fis.read(chunk)) != -1) {
                fos.write(chunk);
            }
            fis.close();
            fos.close();
        } catch (Exception ignored) {
        }
    }

    public static void copyFromInputStreamToPath(InputStream is, String path) {
        try {
            Files.copy(is, Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}
    }
}
