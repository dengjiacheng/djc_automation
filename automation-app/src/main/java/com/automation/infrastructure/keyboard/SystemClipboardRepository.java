package com.automation.infrastructure.keyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

/**
 * 负责统一读取系统剪贴板内容，后续可替换为更复杂的实现。
 */
public class SystemClipboardRepository {

    public CharSequence readText(Context context) {
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            return null;
        }
        ClipData primaryClip = clipboardManager.getPrimaryClip();
        if (primaryClip == null || primaryClip.getItemCount() == 0) {
            return null;
        }
        return primaryClip.getItemAt(0).coerceToText(context);
    }
}
