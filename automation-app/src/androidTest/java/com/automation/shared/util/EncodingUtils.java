package com.automation.shared.util;

import android.os.Build;
import android.util.Base64;

/**
 * Base64 编解码兼容工具。
 */
public final class EncodingUtils {

    private EncodingUtils() {
    }

    public static String encodeBase64(byte[] data) {
        if (data == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return java.util.Base64.getEncoder().encodeToString(data);
        }
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }
}
