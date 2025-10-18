package com.automation.application.scenario;

import android.util.Log;

import com.automation.domain.command.CommandParameter;
import com.automation.domain.scenario.script.ScriptParameterSpec;
import com.automation.domain.scenario.script.ScriptRepository;
import com.automation.domain.scenario.script.ScenarioScript;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 负责脚本的读取、枚举以及元数据构建。
 */
public final class ScenarioCatalog {

    private static final String TAG = "ScenarioCatalog";

    private final ScriptRepository scriptRepository;

    public ScenarioCatalog(ScriptRepository scriptRepository) {
        this.scriptRepository = Objects.requireNonNull(scriptRepository, "scriptRepository");
    }

    public ScenarioScript requireScript(String taskName) {
        try {
            return scriptRepository.load(taskName);
        } catch (IOException ex) {
            throw new IllegalStateException("加载脚本失败: " + taskName, ex);
        }
    }

    public List<String> listScriptNames() {
        try {
            return scriptRepository.listScriptNames();
        } catch (IOException ex) {
            Log.w(TAG, "列举脚本失败", ex);
            return Collections.emptyList();
        }
    }

    public JSONObject buildCatalogMetadata() {
        JSONObject root = new JSONObject();
        JSONArray scriptsArray = new JSONArray();
        for (String name : listScriptNames()) {
            try {
                ScenarioScript script = requireScript(name);
                scriptsArray.put(buildScriptMetadata(script));
            } catch (Exception ex) {
                Log.w(TAG, "构建脚本元数据失败: " + name, ex);
            }
        }
        try {
            root.put("scripts", scriptsArray);
        } catch (JSONException ignored) {
        }
        return root;
    }

    public JSONObject buildScriptMetadata(ScenarioScript script) {
        JSONObject scriptJson = new JSONObject();
        try {
            scriptJson.put("name", script.name());
            if (!script.version().isEmpty()) {
                scriptJson.put("version", script.version());
            }
            if (!script.description().isEmpty()) {
                scriptJson.put("description", script.description());
            }
            JSONArray paramsArray = new JSONArray();
            for (ScriptParameterSpec spec : script.parameterSpecs()) {
                JSONObject specJson = new JSONObject();
                specJson.put("name", spec.name());
                if (spec.type() != null) {
                    specJson.put("type", spec.type());
                }
                specJson.put("required", spec.required());
                if (spec.description() != null) {
                    specJson.put("description", spec.description());
                }
                if (spec.defaultValue() != null) {
                    specJson.put("default", spec.defaultValue());
                }
                paramsArray.put(specJson);
            }
            scriptJson.put("parameters", paramsArray);
        } catch (JSONException ex) {
            Log.w(TAG, "脚本元数据构建异常: " + script.name(), ex);
        }
        return scriptJson;
    }

    public List<CommandParameter> buildCapabilityParameters(ScenarioScript script) {
        List<CommandParameter> parameters = new ArrayList<>();
        parameters.add(CommandParameter.required("task_name", "string", "脚本名称", script.name()));
        for (ScriptParameterSpec spec : script.parameterSpecs()) {
            String type = normalizeParamType(spec.type());
            String name = "config." + spec.name();
            if (spec.required()) {
                parameters.add(CommandParameter.required(name, type, spec.description(), spec.defaultValue()));
            } else {
                parameters.add(CommandParameter.optional(name, type, spec.description(), spec.defaultValue()));
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    private String normalizeParamType(String rawType) {
        if (rawType == null || rawType.isEmpty()) {
            return "string";
        }
        String type = rawType.toLowerCase(Locale.ROOT);
        if ("int".equals(type) || "integer".equals(type)) {
            return "int";
        }
        if ("float".equals(type) || "double".equals(type) || "number".equals(type)) {
            return "number";
        }
        if ("bool".equals(type) || "boolean".equals(type)) {
            return "bool";
        }
        if ("object".equals(type) || "json".equals(type) || "dict".equals(type) || "map".equals(type)) {
            return "object";
        }
        if ("array".equals(type) || "list".equals(type)) {
            return "array";
        }
        if ("file".equals(type)) {
            return "file";
        }
        if ("image".equals(type)) {
            return "image";
        }
        return type;
    }
}
