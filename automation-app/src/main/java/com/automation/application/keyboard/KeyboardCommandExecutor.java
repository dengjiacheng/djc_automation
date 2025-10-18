package com.automation.application.keyboard;

import android.content.Context;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.automation.domain.keyboard.InputConnectionProvider;
import com.automation.domain.keyboard.command.KeyboardCommand;
import com.automation.domain.keyboard.command.KeyboardCommandType;
import com.automation.infrastructure.keyboard.SystemClipboardRepository;
import com.automation.shared.codec.Base64TextCodec;

/**
 * 将解析后的指令执行到当前输入法上下文中。
 */
public class KeyboardCommandExecutor {

    private final InputConnectionProvider inputConnectionProvider;
    private final InputConnectionOperations operations;
    private final SystemClipboardRepository clipboardRepository;
    private final Base64TextCodec textCodec;
    private final EditorActionPerformer editorActionPerformer;
    private final InputMethodManager inputMethodManager;

    public KeyboardCommandExecutor(InputConnectionProvider inputConnectionProvider,
                                   InputConnectionOperations operations,
                                   SystemClipboardRepository clipboardRepository,
                                   Base64TextCodec textCodec,
                                   EditorActionPerformer editorActionPerformer,
                                   InputMethodManager inputMethodManager) {
        this.inputConnectionProvider = inputConnectionProvider;
        this.operations = operations;
        this.clipboardRepository = clipboardRepository;
        this.textCodec = textCodec;
        this.editorActionPerformer = editorActionPerformer;
        this.inputMethodManager = inputMethodManager;
    }

    public String execute(Context context, KeyboardCommand command, IBinder windowToken) {
        if (command == null) {
            return null;
        }
        InputConnection inputConnection = inputConnectionProvider.current();
        switch (command.getType()) {
            case INPUT_TEXT:
                commitText(inputConnection, command.getTextPayload());
                return null;
            case SET_TEXT:
                setText(inputConnection, command.getTextPayload());
                return null;
            case CLEAR_TEXT:
                operations.clearText(inputConnection);
                return null;
            case INPUT_KEYCODE:
                sendKeyCode(inputConnection, command.getCodePayload());
                return null;
            case EDITOR_CODE:
                performEditorAction(inputConnection, command.getCodePayload());
                return null;
            case GET_CLIPBOARD:
                String encoded = textCodec.encode(clipboardRepository.readText(context));
                return encoded != null ? encoded : "";
            case HIDE_KEYBOARD:
                hideKeyboard(windowToken);
                return null;
            case SHOW_KEYBOARD:
                showKeyboard(windowToken);
                return null;
            case SMART_ENTER:
                return editorActionPerformer.performImeAction(inputConnection);
            default:
                return null;
        }
    }

    private void commitText(InputConnection inputConnection, String encodedText) {
        if (inputConnection == null) {
            return;
        }
        String text = textCodec.decode(encodedText);
        operations.commitText(inputConnection, text);
    }

    private void setText(InputConnection inputConnection, String encodedText) {
        if (inputConnection == null) {
            return;
        }
        inputConnection.beginBatchEdit();
        operations.clearText(inputConnection);
        commitText(inputConnection, encodedText);
        inputConnection.endBatchEdit();
    }

    private void sendKeyCode(InputConnection inputConnection, int keyCode) {
        if (inputConnection == null || keyCode == -1) {
            return;
        }
        inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    }

    private void performEditorAction(InputConnection inputConnection, int actionCode) {
        if (inputConnection == null || actionCode == -1) {
            return;
        }
        inputConnection.performEditorAction(actionCode);
    }

    private void hideKeyboard(IBinder windowToken) {
        if (inputMethodManager != null && windowToken != null) {
            inputMethodManager.hideSoftInputFromWindow(windowToken, 0);
        }
    }

    private void showKeyboard(IBinder windowToken) {
        if (inputMethodManager != null && windowToken != null) {
            inputMethodManager.showSoftInputFromInputMethod(windowToken, 0);
        }
    }
}
