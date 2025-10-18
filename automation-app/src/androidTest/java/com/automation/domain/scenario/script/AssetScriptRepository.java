package com.automation.domain.scenario.script;

import android.content.Context;
import android.content.res.AssetManager;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 从 androidTest assets 中加载脚本配置。
 */
public final class AssetScriptRepository implements ScriptRepository {

    private final AssetManager assets;
    private final Yaml yaml = new Yaml();

    public AssetScriptRepository(Context context) {
        this.assets = Objects.requireNonNull(context, "context").getAssets();
    }

    @Override
    public ScenarioScript load(String scriptName) throws IOException {
        Objects.requireNonNull(scriptName, "scriptName");
        Map<String, Object> project = loadYaml(pathOf(scriptName, "project.yaml"));
        Map<String, Object> scenes = loadYaml(pathOf(scriptName, "scenes.yaml"));

        String name = readString(project, List.of("metadata", "name"), scriptName);
        String version = readString(project, List.of("metadata", "version"), "");
        String description = readString(project, List.of("metadata", "description"), "");
        String initSceneId = readString(project, List.of("entry", "init_scene"), null);
        ParameterDefinitions parameterDefinitions = parseParameterDefinitions(project);

        List<Map<String, Object>> rawScenes = readList(scenes, "scenes");
        List<SceneConfig> sceneConfigs = new ArrayList<>(rawScenes.size());
        for (Map<String, Object> raw : rawScenes) {
            sceneConfigs.add(parseScene(raw));
        }

        return new ScenarioScript(
                name,
                version,
                description,
                initSceneId,
                sceneConfigs,
                parameterDefinitions.defaults,
                parameterDefinitions.specs
        );
    }

    @Override
    public List<String> listScriptNames() throws IOException {
        String[] entries = assets.list("scripts");
        if (entries == null || entries.length == 0) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            String projectPath = pathOf(entry, "project.yaml");
            try (InputStream ignored = assets.open(projectPath)) {
                result.add(entry);
            } catch (IOException ignored) {
                // skip entries without project.yaml
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    private SceneConfig parseScene(Map<String, Object> raw) {
        String id = (String) raw.get("id");
        String description = (String) raw.getOrDefault("description", "");
        Map<String, Object> signatureMap = safeMap(raw.get("signature"));
        SignatureConfig signatureConfig = null;
        if (signatureMap != null && !signatureMap.isEmpty()) {
            signatureConfig = new SignatureConfig(
                    readSelectorList(signatureMap, "required_all"),
                    readSelectorList(signatureMap, "required_any"),
                    readSelectorList(signatureMap, "forbidden_any"),
                    readSelectorList(signatureMap, "forbidden_all")
            );
        }
        String handlerName = raw.get("handler") instanceof String handler ? handler : null;
        List<String> pruneScenes = readStringList(raw, "del_scenes");
        return new SceneConfig(id, description, signatureConfig, handlerName, pruneScenes);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> safeMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return result;
        }
        return null;
    }

    private List<Map<String, Object>> readSelectorList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>(list.size());
            for (Object item : list) {
                Map<String, Object> selector = safeMap(item);
                if (selector != null && !selector.isEmpty()) {
                    result.add(selector);
                }
            }
            return result;
        }
        Map<String, Object> selector = safeMap(value);
        if (selector != null && !selector.isEmpty()) {
            return List.of(selector);
        }
        return List.of();
    }

    private List<String> readStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return List.of(value.toString());
    }

    private ParameterDefinitions parseParameterDefinitions(Map<String, Object> project) {
        Map<String, Object> parameters = safeMap(project.get("parameters"));
        if (parameters == null) {
            return new ParameterDefinitions();
        }
        ParameterDefinitions definitions = new ParameterDefinitions();
        collectParameterDefinitions(parameters.get("required"), true, definitions);
        collectParameterDefinitions(parameters.get("optional"), false, definitions);
        return definitions;
    }

    @SuppressWarnings("unchecked")
    private void collectParameterDefinitions(Object value, boolean required, ParameterDefinitions target) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object name = map.get("name");
            if (name == null) {
                continue;
            }
            String paramName = name.toString();
            String type = map.get("type") != null ? map.get("type").toString() : null;
            String description = map.get("description") != null ? map.get("description").toString() : null;
            Object defaultValue = map.get("default");
            if (defaultValue != null) {
                target.defaults.put(paramName, defaultValue);
            }
            target.specs.add(new ScriptParameterSpec(paramName, type, description, required, defaultValue));
        }
    }

    private static final class ParameterDefinitions {
        final Map<String, Object> defaults = new LinkedHashMap<>();
        final List<ScriptParameterSpec> specs = new ArrayList<>();
    }

    private Map<String, Object> loadYaml(String path) throws IOException {
        try (InputStream in = assets.open(path)) {
            Object data = yaml.load(in);
            if (data instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        result.put(entry.getKey().toString(), entry.getValue());
                    }
                }
                return result;
            }
            return Map.of();
        }
    }

    private String pathOf(String scriptName, String filename) {
        return "scripts/" + scriptName + "/" + filename;
    }

    private List<Map<String, Object>> readList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>(list.size());
            for (Object item : list) {
                Map<String, Object> entry = safeMap(item);
                if (entry != null) {
                    result.add(entry);
                }
            }
            return result;
        }
        return List.of();
    }

    private String readString(Map<String, Object> root, List<String> path, String defaultValue) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return defaultValue;
            }
            current = map.get(key);
        }
        return current != null ? current.toString() : defaultValue;
    }
}
