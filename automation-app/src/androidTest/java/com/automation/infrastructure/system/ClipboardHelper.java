package com.automation.infrastructure.system;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

/**
 * 剪贴板工具
 * 从 atx-agent AutomatorServiceImpl 提取
 */
public class ClipboardHelper {
    private ClipboardManager clipboard;

    public ClipboardHelper(Context context) {
        this.clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /**
     * 设置剪贴板内容 - 原始 atx-agent 实现
     */
    public void setClipboard(String label, String text) {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    /**
     * 获取剪贴板内容 - 原始 atx-agent 实现
     */
    public String getClipboard() {
        final ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0 && clipboard.getPrimaryClip().getItemAt(0).getText() != null) {
            return clipboard.getPrimaryClip().getItemAt(0).getText().toString();
        }
        return null;
    }
}
