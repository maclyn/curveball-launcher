package com.inipage.homelylauncher.utils;

import java.io.FileInputStream;
import java.io.FileOutputStream;

public class FileUtils {

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
}
