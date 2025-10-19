package com.automation.application.scenario;

import android.util.Log;

import com.automation.domain.command.CommandParameter;
import com.automation.domain.command.ParameterValidator;
import com.automation.domain.scenario.script.ScriptParameterSpec;
import com.automation.domain.scenario.script.ScenarioScript;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.Nullable;

import com.automation.application.scenario.TemplateAssetManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 负责脚本参数的合并、校验与归一化。
 */
public final class ScenarioParameterBinder {

    private static final String TAG = "ScenarioParamBinder";

    private final ScenarioCatalog catalog;
    private TemplateAssetManager assetManager;

    public ScenarioParameterBinder(ScenarioCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public void setAssetManager(@Nullable TemplateAssetManager manager) {
        this.assetManager = manager;
    }

    public void clearAssetCache() {
        if (assetManager != null) {
            assetManager.clearCache();
        }
    }

    public List<CommandParameter> baseParameters() {
        return List.of(
                CommandParameter.required("task_name", "string", "脚本名称"),
                CommandParameter.optional("config", "object", "脚本配置(JSON)", new JSONObject())
        );
    }

    public List<CommandParameter> capabilityParameters(ScenarioScript script) {
        return catalog.buildCapabilityParameters(script);
    }

    public ScenarioTaskRequest bind(JSONObject params) throws JSONException {
        Objects.requireNonNull(params, "params");
        String taskName = params.optString("task_name", "").trim();
        if (taskName.isEmpty()) {
            Log.e(TAG, "task_name is empty in params: " + params);
            throw new IllegalArgumentException("task_name 不能为空");
        }

        ScenarioScript script = catalog.requireScript(taskName);
        Log.d(TAG, "Loaded script definition for '" + script.name() + "'");

        JSONObject payload = new JSONObject();
        payload.put("task_name", taskName);
        JSONObject configJson = params.optJSONObject("config");
        if (configJson != null) {
            payload.put("config", new JSONObject(configJson.toString()));
        } else {
            payload.put("config", new JSONObject());
        }

        ParameterValidator validator = new ParameterValidator(parameterSchema(script));
        JSONObject normalized = validator.validate(payload);

        if (assetManager != null) {
            resolveFileParameters(script, normalized);
        }

        String normalizedTask = normalized.optString("task_name", taskName);
        JSONObject normalizedConfig = normalized.optJSONObject("config");

        Map<String, Object> contextData = new LinkedHashMap<>(script.defaultParameters());
        if (normalizedConfig != null) {
            contextData.putAll(jsonToMap(normalizedConfig));
        }
        contextData.put("task_name", normalizedTask);

        return new ScenarioTaskRequest(normalizedTask, script, contextData,
                normalizedConfig != null ? normalizedConfig : new JSONObject());
    }

    private void resolveFileParameters(ScenarioScript script, JSONObject normalized) throws JSONException {
        if (normalized == null || !normalized.has("config")) {
            return;
        }
        JSONObject config = normalized.optJSONObject("config");
        if (config == null) {
            return;
        }
        for (ScriptParameterSpec spec : script.parameterSpecs()) {
            String type = normalizeParamType(spec.type());
            if (!"file".equals(type) && !"image".equals(type)) {
                continue;
            }
            if (!config.has(spec.name())) {
                continue;
            }
            Object raw = config.get(spec.name());
            if (raw instanceof JSONObject json) {
                try {
                    JSONObject resolved = assetManager.resolve(json, type);
                    if (resolved != null) {
                        config.put(spec.name(), resolved);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to resolve asset parameter: " + spec.name(), e);
                    throw new IllegalStateException("下载脚本资源失败: " + spec.name(), e);
                }
            }
        }
    }

    private List<CommandParameter> parameterSchema(ScenarioScript script) {
        List<CommandParameter> parameters = new ArrayList<>();
        parameters.add(CommandParameter.required("task_name", "string", "脚本名称", script.name()));
        parameters.add(CommandParameter.optional("config", "object", "脚本配置(JSON)", new JSONObject()));
        for (ScriptParameterSpec spec : script.parameterSpecs()) {
            String type = normalizeParamType(spec.type());
            String name = "config." + spec.name();
            if (spec.required()) {
                parameters.add(CommandParameter.required(name, type, spec.description(), spec.defaultValue()));
            } else {
                parameters.add(CommandParameter.optional(name, type, spec.description(), spec.defaultValue()));
            }
        }
        return parameters;
    }

    private Map<String, Object> jsonToMap(JSONObject source) throws JSONException {
        Map<String, Object> result = new LinkedHashMap<>();
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = source.get(key);
            result.put(key, convertJsonValue(value));
        }
        return result;
    }

    private List<Object> jsonToList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            list.add(convertJsonValue(array.get(i)));
        }
        return list;
    }

    private Object convertJsonValue(Object value) throws JSONException {
        if (value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof JSONObject object) {
            return jsonToMap(object);
        }
        if (value instanceof JSONArray array) {
            return jsonToList(array);
        }
        return value;
    }

    private String normalizeParamType(String rawType) {
        if (rawType == null || rawType.isEmpty()) {
            return "string";
        }
        String type = rawType.toLowerCase(Locale.ROOT);
        return switch (type) {
            case "int", "integer" -> "int";
            case "float", "double", "number" -> "number";
            case "bool", "boolean" -> "bool";
            case "object", "json", "dict", "map" -> "object";
            case "array", "list" -> "array";
            case "file" -> "file";
            case "image" -> "image";
            default -> type;
        };
    }
}
