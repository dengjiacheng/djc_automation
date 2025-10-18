package com.automation.application.scenario;

import com.automation.domain.scenario.script.ScenarioScript;

import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 表示一次脚本任务执行所需的上下文。
 */
public final class ScenarioTaskRequest {

    private final String taskName;
    private final ScenarioScript script;
    private final Map<String, Object> contextData;
    private final JSONObject normalizedConfig;

    public ScenarioTaskRequest(String taskName,
                               ScenarioScript script,
                               Map<String, Object> contextData,
                               JSONObject normalizedConfig) {
        this.taskName = Objects.requireNonNull(taskName, "taskName");
        this.script = Objects.requireNonNull(script, "script");
        this.contextData = contextData != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(contextData))
                : Map.of();
        this.normalizedConfig = cloneJson(normalizedConfig);
    }

    public String taskName() {
        return taskName;
    }

    public ScenarioScript script() {
        return script;
    }

    public Map<String, Object> contextData() {
        return contextData;
    }

    public JSONObject normalizedConfig() {
        return cloneJson(normalizedConfig);
    }

    private static JSONObject cloneJson(JSONObject source) {
        if (source == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(source.toString());
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法克隆 JSON 对象", ex);
        }
    }
}
