package com.inipage.homelylauncher.utils;

import android.Manifest;
import android.content.Context;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;

public class PhoneUtils {

    public static boolean isInCall(Context context) {
        final boolean hasPhonePermission = PermissionUtils.checkPermission(
            Manifest.permission.READ_PHONE_STATE, context);

        if (!hasPhonePermission) {
            return false;
        }
        @Nullable final TelephonyManager tm =
            (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return tm != null && tm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK;
    }
}
