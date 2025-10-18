package com.automation.application.keyboard;

import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * 管理与编辑器相关的 action 执行，便于被命令执行器复用。
 */
public class EditorActionPerformer {

    private int lastImeAction = EditorInfo.IME_ACTION_NONE;

    public void setLastImeAction(int imeAction) {
        lastImeAction = imeAction;
    }

    public String performImeAction(InputConnection inputConnection) {
        if (inputConnection == null) {
            return null;
        }
        switch (lastImeAction) {
            case EditorInfo.IME_ACTION_DONE:
            case EditorInfo.IME_ACTION_GO:
            case EditorInfo.IME_ACTION_NEXT:
            case EditorInfo.IME_ACTION_SEARCH:
            case EditorInfo.IME_ACTION_SEND:
            case EditorInfo.IME_ACTION_NONE:
                inputConnection.performEditorAction(lastImeAction);
                return String.valueOf(lastImeAction);
            default:
                inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                return "Enter";
        }
    }
}
