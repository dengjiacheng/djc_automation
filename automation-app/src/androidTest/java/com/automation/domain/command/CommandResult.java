package com.automation.domain.command;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 表示指令执行的归一化结果。
 */
public final class CommandResult {

    private final boolean success;
    private final Object payload;
    private final String message;

    private CommandResult(boolean success, Object payload, String message) {
        this.success = success;
        this.payload = payload;
        this.message = message;
    }

    public static CommandResult success(Object payload) {
        return new CommandResult(true, payload, null);
    }

    public static CommandResult success(Object payload, String message) {
        return new CommandResult(true, payload, message);
    }

    public static CommandResult successMessage(String message) {
        return new CommandResult(true, null, message);
    }

    public static CommandResult success() {
        return new CommandResult(true, null, null);
    }

    public static CommandResult failure(String message) {
        return new CommandResult(false, null, message);
    }

    public static CommandResult failure(Object payload, String message) {
        return new CommandResult(false, payload, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public Object payload() {
        return payload;
    }

    public String message() {
        return message;
    }

    /**
     * 将结果渲染为字符串，供传输层直接发送。
     */
    public String renderPayload() {
        if (payload == null) {
            return message;
        }
        if (payload instanceof String str) {
            return str;
        }
        if (payload instanceof JSONObject json) {
            return json.toString();
        }
        if (payload instanceof JSONArray array) {
            return array.toString();
        }
        Object wrapped = JSONObject.wrap(payload);
        if (wrapped == null || wrapped == JSONObject.NULL) {
            return message != null ? message : null;
        }
        if (wrapped instanceof JSONObject jsonObject) {
            return jsonObject.toString();
        }
        if (wrapped instanceof JSONArray jsonArray) {
            return jsonArray.toString();
        }
        return wrapped.toString();
    }
}
