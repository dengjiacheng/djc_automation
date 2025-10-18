package com.automation.infrastructure.network;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Transfer object for all websocket interactions.
 */
public final class AutomationMessage {

    private final String type;
    private final JSONObject data;

    public AutomationMessage(String type, JSONObject data) {
        this.type = type;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public JSONObject getData() {
        return data;
    }

    public String toJson() throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("type", type);
        if (data != null) {
            payload.put("data", data);
        }
        return payload.toString();
    }

    public static AutomationMessage fromJson(String raw) throws JSONException {
        JSONObject json = new JSONObject(raw);
        String type = json.optString("type");
        JSONObject data = json.optJSONObject("data");
        return new AutomationMessage(type, data);
    }

    public static AutomationMessage heartbeat(int battery, String networkType, String task) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("battery", battery);
        payload.put("network_type", networkType);
        payload.put("current_task", task);
        return new AutomationMessage("heartbeat", payload);
    }

    public static AutomationMessage commandResult(String commandId,
                                                  boolean success,
                                                  String result,
                                                  String errorMessage,
                                                  String userId,
                                                  String deviceId,
                                                  String action) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("command_id", commandId);
        payload.put("status", success ? "success" : "failed");
        if (result != null) {
            payload.put("result", result);
        }
        if (errorMessage != null) {
            payload.put("error_message", errorMessage);
        }
        if (userId != null) {
            payload.put("user_id", userId);
        }
        if (deviceId != null) {
            payload.put("device_id", deviceId);
        }
        if (action != null) {
            payload.put("action", action);
        }
        return new AutomationMessage("result", payload);
    }

    public static AutomationMessage sessionInit(JSONObject data) {
        return new AutomationMessage("session_init", data);
    }

    public static AutomationMessage log(String level, String message) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("type", level);
        payload.put("message", message);
        return new AutomationMessage("log", payload);
    }
}
