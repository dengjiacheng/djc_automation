package com.automation.infrastructure.keyboard.service;

import android.content.Context;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.IBinder;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.automation.R;
import com.automation.application.keyboard.EditorActionPerformer;
import com.automation.application.keyboard.InputConnectionOperations;
import com.automation.application.keyboard.KeyboardCommandExecutor;
import com.automation.domain.keyboard.InputConnectionProvider;
import com.automation.domain.keyboard.WindowTokenProvider;
import com.automation.infrastructure.keyboard.KeyboardBroadcastReceiver;
import com.automation.infrastructure.keyboard.KeyboardIntentParser;
import com.automation.infrastructure.keyboard.SystemClipboardRepository;
import com.automation.shared.codec.Base64TextCodec;

/**
 * 新的键盘服务实现，将广播解析、执行与系统交互解耦。
 */
public class AdbKeyboardService extends InputMethodService
        implements InputConnectionProvider, WindowTokenProvider {

    private static final String[] INTENT_ACTIONS = new String[]{
            "ADB_KEYBOARD_SMART_ENTER",
            "ADB_KEYBOARD_INPUT_TEXT",
            "ADB_KEYBOARD_CLEAR_TEXT",
            "ADB_KEYBOARD_SET_TEXT",
            "ADB_KEYBOARD_INPUT_KEYCODE",
            "ADB_KEYBOARD_EDITOR_CODE",
            "ADB_KEYBOARD_GET_CLIPBOARD",
            "ADB_KEYBOARD_HIDE",
            "ADB_KEYBOARD_SHOW"
    };

    private KeyboardView keyboardView;
    private Keyboard keyboard;
    private KeyboardBroadcastReceiver broadcastReceiver;

    private final EditorActionPerformer editorActionPerformer = new EditorActionPerformer();
    private final KeyboardIntentParser intentParser = new KeyboardIntentParser();
    private final SystemClipboardRepository clipboardRepository = new SystemClipboardRepository();
    private final Base64TextCodec base64TextCodec = new Base64TextCodec();
    private final InputConnectionOperations connectionOperations = new InputConnectionOperations();

    private KeyboardCommandExecutor commandExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        commandExecutor = new KeyboardCommandExecutor(
                this,
                connectionOperations,
                clipboardRepository,
                base64TextCodec,
                editorActionPerformer,
                imm
        );
        broadcastReceiver = new KeyboardBroadcastReceiver(intentParser, commandExecutor, this);
        registerReceiver(broadcastReceiver, buildIntentFilter());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
            broadcastReceiver = null;
        }
    }

    @Override
    public View onCreateInputView() {
        keyboardView = (KeyboardView) getLayoutInflater().inflate(R.layout.input, null);
        keyboard = new Keyboard(this, R.xml.keyboard);
        keyboardView.setKeyboard(keyboard);
        keyboardView.setOnKeyboardActionListener(new SimpleActionListener());
        return keyboardView;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        editorActionPerformer.setLastImeAction(attribute.imeOptions & EditorInfo.IME_MASK_ACTION);
    }

    @Override
    public InputConnection current() {
        return getCurrentInputConnection();
    }

    @Override
    public IBinder provide() {
        if (getWindow() == null) {
            return null;
        }
        Window window = getWindow().getWindow();
        if (window == null || window.getDecorView() == null) {
            return null;
        }
        return window.getDecorView().getWindowToken();
    }

    private IntentFilter buildIntentFilter() {
        IntentFilter filter = new IntentFilter();
        for (String action : INTENT_ACTIONS) {
            filter.addAction(action);
        }
        return filter;
    }

    private class SimpleActionListener implements KeyboardView.OnKeyboardActionListener {

        @Override
        public void onPress(int primaryCode) {
        }

        @Override
        public void onRelease(int primaryCode) {
        }

        @Override
        public void onKey(int primaryCode, int[] keyCodes) {
            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection == null) {
                return;
            }
            switch (primaryCode) {
                case -10:
                    connectionOperations.clearText(inputConnection);
                    break;
                case -8:
                    editorActionPerformer.performImeAction(inputConnection);
                    break;
                case -5:
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showInputMethodPicker();
                    }
                    break;
                default:
                    if (primaryCode > 0) {
                        inputConnection.commitText(String.valueOf((char) primaryCode), 1);
                    }
            }
        }

        @Override
        public void onText(CharSequence text) {
            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                inputConnection.commitText(text, 1);
            }
        }

        @Override
        public void swipeLeft() {
        }

        @Override
        public void swipeRight() {
        }

        @Override
        public void swipeDown() {
        }

        @Override
        public void swipeUp() {
        }
    }
}
