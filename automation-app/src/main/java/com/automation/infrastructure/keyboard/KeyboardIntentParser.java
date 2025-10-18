package com.automation.infrastructure.keyboard;

import android.content.Intent;

import com.automation.domain.keyboard.command.KeyboardCommand;
import com.automation.domain.keyboard.command.KeyboardCommandType;

/**
 * 将外部广播 Intent 解析为内部的 KeyboardCommand。
 */
public class KeyboardIntentParser {

    public KeyboardCommand parse(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return null;
        }
        KeyboardCommandType type = mapAction(intent.getAction());
        if (type == null) {
            return null;
        }
        switch (type) {
            case INPUT_TEXT:
            case SET_TEXT:
                return KeyboardCommand.withText(type, intent.getStringExtra("text"));
            case INPUT_KEYCODE:
            case EDITOR_CODE:
                return KeyboardCommand.withCode(type, intent.getIntExtra("code", -1));
            default:
                return KeyboardCommand.of(type);
        }
    }

    private KeyboardCommandType mapAction(String action) {
        switch (action) {
            case "ADB_KEYBOARD_SMART_ENTER":
                return KeyboardCommandType.SMART_ENTER;
            case "ADB_KEYBOARD_INPUT_TEXT":
                return KeyboardCommandType.INPUT_TEXT;
            case "ADB_KEYBOARD_CLEAR_TEXT":
                return KeyboardCommandType.CLEAR_TEXT;
            case "ADB_KEYBOARD_SET_TEXT":
                return KeyboardCommandType.SET_TEXT;
            case "ADB_KEYBOARD_INPUT_KEYCODE":
                return KeyboardCommandType.INPUT_KEYCODE;
            case "ADB_KEYBOARD_EDITOR_CODE":
                return KeyboardCommandType.EDITOR_CODE;
            case "ADB_KEYBOARD_GET_CLIPBOARD":
                return KeyboardCommandType.GET_CLIPBOARD;
            case "ADB_KEYBOARD_HIDE":
                return KeyboardCommandType.HIDE_KEYBOARD;
            case "ADB_KEYBOARD_SHOW":
                return KeyboardCommandType.SHOW_KEYBOARD;
            default:
                return null;
        }
    }
}
