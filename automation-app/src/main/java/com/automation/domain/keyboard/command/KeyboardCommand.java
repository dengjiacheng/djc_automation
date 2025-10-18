package com.automation.domain.keyboard.command;

/**
 * 封装一次指令及其附加参数，便于后续扩展更多字段。
 */
public class KeyboardCommand {

    private final KeyboardCommandType type;
    private final String textPayload;
    private final int codePayload;

    private KeyboardCommand(KeyboardCommandType type, String textPayload, int codePayload) {
        this.type = type;
        this.textPayload = textPayload;
        this.codePayload = codePayload;
    }

    public static KeyboardCommand of(KeyboardCommandType type) {
        return new KeyboardCommand(type, null, -1);
    }

    public static KeyboardCommand withText(KeyboardCommandType type, String textPayload) {
        return new KeyboardCommand(type, textPayload, -1);
    }

    public static KeyboardCommand withCode(KeyboardCommandType type, int codePayload) {
        return new KeyboardCommand(type, null, codePayload);
    }

    public KeyboardCommandType getType() {
        return type;
    }

    public String getTextPayload() {
        return textPayload;
    }

    public int getCodePayload() {
        return codePayload;
    }
}
