package com.automation.domain.command;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Validates command parameters, applying defaults and basic type conversions.
 */
public final class ParameterValidator {

    private static final String TAG = "ParameterValidator";

    private final List<CommandParameter> parameters;

    public ParameterValidator(List<CommandParameter> parameters) {
        this.parameters = parameters != null ? parameters : Collections.emptyList();
    }

    public JSONObject validate(JSONObject rawParams) throws JSONException {
        JSONObject source = rawParams != null ? cloneJSONObject(rawParams) : new JSONObject();
        JSONObject result = cloneJSONObject(source);

        for (CommandParameter parameter : parameters) {
            Object rawValue = readPath(source, parameter.name());
            boolean present = isPresent(rawValue);
            Object valueToApply;

            if (!present) {
                if (parameter.defaultValue() != null) {
                    valueToApply = parameter.defaultValue();
                } else if (parameter.required()) {
                    Log.e(TAG, "Missing required parameter: " + parameter.name());
                    throw new IllegalArgumentException("缺少参数: " + parameter.name());
                } else {
                    continue;
                }
            } else {
                valueToApply = rawValue;
            }

            Object converted = convertValue(parameter, valueToApply);
            if (!isPresent(converted)) {
                if (parameter.required()) {
                    Log.e(TAG, "Required parameter became empty after conversion: " + parameter.name());
                    throw new IllegalArgumentException("参数不能为空: " + parameter.name());
                } else {
                    removePath(result, parameter.name());
                    continue;
                }
            }

            writePath(result, parameter.name(), converted);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Parameter '" + parameter.name() + "' normalized to: " + converted);
            }
        }

        return result;
    }

    private Object convertValue(CommandParameter parameter, Object raw) {
        ParameterType type = parameter.parameterType();
        return switch (type) {
            case INT -> convertToInt(parameter, raw);
            case FLOAT -> convertToDouble(parameter, raw);
            case BOOL -> convertToBoolean(parameter, raw);
            case OBJECT, JSON -> convertToJSONObject(parameter, raw);
            case ARRAY -> convertToJSONArray(parameter, raw);
            case FILE, IMAGE -> convertToFilePayload(parameter, raw);
            case ENUM -> convertToEnum(parameter, raw);
            case STRING -> convertToString(parameter, raw);
        };
    }

    private String convertToString(CommandParameter parameter, Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        String value = raw.toString();
        if (parameter.required()
                && !parameter.constraints().allowBlank()
                && value.trim().isEmpty()) {
            throw new IllegalArgumentException("参数不能为空: " + parameter.name());
        }
        if (parameter.constraints().hasEnumValues()) {
            Set<String> enums = parameter.constraints().enumValues();
            if (!enums.contains(value)) {
                throw new IllegalArgumentException("参数不在可选范围内: " + parameter.name());
            }
        }
        return value;
    }

    private Integer convertToInt(CommandParameter parameter, Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException ex) {
            Log.e(TAG, "Failed to convert parameter '" + parameter.name() + "' to int: " + raw, ex);
            throw new IllegalArgumentException("参数不是整数: " + parameter.name());
        }
    }

    private Double convertToDouble(CommandParameter parameter, Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(raw.toString().trim());
        } catch (NumberFormatException ex) {
            Log.e(TAG, "Failed to convert parameter '" + parameter.name() + "' to number: " + raw, ex);
            throw new IllegalArgumentException("参数不是数字: " + parameter.name());
        }
    }

    private Boolean convertToBoolean(CommandParameter parameter, Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof Boolean bool) {
            return bool;
        }
        String text = raw.toString().trim().toLowerCase(Locale.ROOT);
        if ("1".equals(text) || "true".equals(text)) {
            return Boolean.TRUE;
        }
        if ("0".equals(text) || "false".equals(text)) {
            return Boolean.FALSE;
        }
        Log.e(TAG, "Failed to convert parameter '" + parameter.name() + "' to boolean: " + raw);
        throw new IllegalArgumentException("参数不是布尔值: " + parameter.name());
    }

    private JSONObject convertToJSONObject(CommandParameter parameter, Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof JSONObject json) {
            return cloneJSONObject(json);
        }
        if (raw instanceof String text) {
            try {
                return new JSONObject(text);
            } catch (JSONException ex) {
                Log.e(TAG, "Failed to convert parameter '" + parameter.name() + "' to JSON object: " + raw, ex);
                throw new IllegalArgumentException("参数不是合法的 JSON 对象: " + parameter.name());
            }
        }
        Log.e(TAG, "Parameter '" + parameter.name() + "' is not a JSON object: " + raw);
        throw new IllegalArgumentException("参数不是合法的 JSON 对象: " + parameter.name());
    }

    private JSONArray convertToJSONArray(CommandParameter parameter, Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (raw instanceof JSONArray array) {
            return cloneJSONArray(array);
        }
        if (raw instanceof Collection<?> collection) {
            return new JSONArray(collection);
        }
        if (raw instanceof String text) {
            try {
                return new JSONArray(text);
            } catch (JSONException ex) {
                Log.e(TAG, "Failed to convert parameter '" + parameter.name() + "' to JSON array: " + raw, ex);
                throw new IllegalArgumentException("参数不是合法的 JSON 数组: " + parameter.name());
            }
        }
        Log.e(TAG, "Parameter '" + parameter.name() + "' is not a JSON array: " + raw);
        throw new IllegalArgumentException("参数不是合法的 JSON 数组: " + parameter.name());
    }

    private JSONObject convertToFilePayload(CommandParameter parameter, Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        try {
            JSONObject payload;
            if (raw instanceof JSONObject json) {
                payload = cloneJSONObject(json);
            } else {
                payload = new JSONObject();
                payload.put("value", raw.toString());
            }

            String source = payload.optString("source", "").trim();
            if (source.isEmpty()) {
                String value = payload.optString("value", "");
                if (looksLikeUrl(value)) {
                    source = "url";
                } else if (looksLikePath(value)) {
                    source = "path";
                } else {
                    source = "base64";
                }
                payload.put("source", source);
            }

            if (!payload.has("value")) {
                throw new IllegalArgumentException("文件参数缺少 value 字段: " + parameter.name());
            }

            if (parameter.parameterType() == ParameterType.IMAGE && !payload.has("mime")) {
                payload.put("mime", "image/*");
            }

            payload.put("type", parameter.parameterType().name().toLowerCase(Locale.ROOT));
            return payload;
        } catch (JSONException ex) {
            Log.e(TAG, "File parameter format error for '" + parameter.name() + "'", ex);
            throw new IllegalArgumentException("文件参数格式错误: " + parameter.name(), ex);
        }
    }

    private Object convertToEnum(CommandParameter parameter, Object raw) {
        String value = convertToString(parameter, raw);
        if (value == null) {
            return null;
        }
        Set<String> enums = parameter.constraints().enumValues();
        if (!enums.contains(value)) {
            throw new IllegalArgumentException("参数不在可选范围内: " + parameter.name());
        }
        return value;
    }

    private boolean looksLikeUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("content://")
                || lower.startsWith("file://");
    }

    private boolean looksLikePath(String value) {
        if (value == null) {
            return false;
        }
        return value.startsWith("/") || value.startsWith("sdcard") || value.startsWith("./");
    }

    private boolean isPresent(Object value) {
        return value != null && value != JSONObject.NULL;
    }

    private JSONObject cloneJSONObject(JSONObject source) {
        if (source == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(source.toString());
        } catch (JSONException ex) {
            throw new IllegalArgumentException("JSON 对象格式错误", ex);
        }
    }

    private JSONArray cloneJSONArray(JSONArray source) {
        if (source == null) {
            return new JSONArray();
        }
        try {
            return new JSONArray(source.toString());
        } catch (JSONException ex) {
            throw new IllegalArgumentException("JSON 数组格式错误", ex);
        }
    }

    private Object readPath(JSONObject source, String path) {
        if (source == null || path == null || path.isEmpty()) {
            return null;
        }
        String[] parts = path.split("\\.");
        Object current = source;
        for (String part : parts) {
            if (!(current instanceof JSONObject jsonObject)) {
                return null;
            }
            if (!jsonObject.has(part)) {
                return null;
            }
            current = jsonObject.opt(part);
        }
        return current;
    }

    private void writePath(JSONObject target, String path, Object value) throws JSONException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(path, "path");
        String[] parts = path.split("\\.");
        JSONObject current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object existing = current.opt(part);
            if (!(existing instanceof JSONObject)) {
                JSONObject next = new JSONObject();
                current.put(part, next);
                current = next;
            } else {
                current = (JSONObject) existing;
            }
        }
        Object wrapped = JSONObject.wrap(value);
        current.put(parts[parts.length - 1], wrapped);
    }

    private void removePath(JSONObject target, String path) {
        if (target == null || path == null || path.isEmpty()) {
            return;
        }
        String[] parts = path.split("\\.");
        JSONObject current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = current.opt(parts[i]);
            if (!(child instanceof JSONObject)) {
                return;
            }
            current = (JSONObject) child;
        }
        current.remove(parts[parts.length - 1]);
    }
}
