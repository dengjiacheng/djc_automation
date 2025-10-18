package com.automation.application.keyboard;

import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

/**
 * 对 InputConnection 的常用操作进行封装，减小指令执行器复杂度。
 */
public class InputConnectionOperations {

    public void clearText(InputConnection inputConnection) {
        if (inputConnection == null) {
            return;
        }
        android.view.inputmethod.ExtractedText extractedText =
                inputConnection.getExtractedText(new ExtractedTextRequest(), 0);
        if (extractedText != null && extractedText.text != null) {
            inputConnection.deleteSurroundingText(extractedText.text.length(), 0);
        }
    }

    public void commitText(InputConnection inputConnection, String text) {
        if (inputConnection == null || text == null) {
            return;
        }
        inputConnection.commitText(text, 1);
    }
}
