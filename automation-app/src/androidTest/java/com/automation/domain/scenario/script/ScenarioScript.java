package com.automation.domain.scenario.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表示完整脚本的元数据与场景配置。
 */
public final class ScenarioScript {

    private final String name;
    private final String version;
    private final String description;
    private final String initSceneId;
    private final List<SceneConfig> scenes;
    private final Map<String, Object> defaultParameters;
    private final List<ScriptParameterSpec> parameterSpecs;

    public ScenarioScript(String name,
                          String version,
                          String description,
                          String initSceneId,
                          List<SceneConfig> scenes,
                          Map<String, Object> defaultParameters,
                          List<ScriptParameterSpec> parameterSpecs) {
        this.name = Objects.requireNonNull(name, "name");
        this.version = version != null ? version : "";
        this.description = description != null ? description : "";
        this.initSceneId = initSceneId;
        this.scenes = scenes != null
                ? Collections.unmodifiableList(new ArrayList<>(scenes))
                : Collections.unmodifiableList(new ArrayList<SceneConfig>());
        if (defaultParameters != null) {
            this.defaultParameters = Collections.unmodifiableMap(new LinkedHashMap<>(defaultParameters));
        } else {
            this.defaultParameters = Collections.unmodifiableMap(new LinkedHashMap<String, Object>());
        }
        this.parameterSpecs = parameterSpecs != null
                ? Collections.unmodifiableList(new ArrayList<>(parameterSpecs))
                : Collections.unmodifiableList(new ArrayList<ScriptParameterSpec>());
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public String description() {
        return description;
    }

    public String initSceneId() {
        return initSceneId;
    }

    public List<SceneConfig> scenes() {
        return scenes;
    }

    public Map<String, Object> defaultParameters() {
        return defaultParameters;
    }

    public List<ScriptParameterSpec> parameterSpecs() {
        return parameterSpecs;
    }
}
