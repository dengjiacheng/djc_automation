package com.automation.shared.codec;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

/**
 * Base64 编解码工具，抽离便于未来替换实现。
 */
public class Base64TextCodec {

    public String encode(CharSequence text) {
        if (text == null) {
            return null;
        }
        return Base64.encodeToString(text.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    public String decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        byte[] data = Base64.decode(encoded, Base64.NO_WRAP);
        return new String(data, StandardCharsets.UTF_8);
    }
}
