package com.automation.domain.scenario.script;

import com.automation.domain.scenario.SceneSignature;
import com.automation.domain.scenario.SelectorCondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * YAML 中的签名配置。
 */
public final class SignatureConfig {

    private final List<Map<String, Object>> requiredAll;
    private final List<Map<String, Object>> requiredAny;
    private final List<Map<String, Object>> forbiddenAny;
    private final List<Map<String, Object>> forbiddenAll;

    public SignatureConfig(List<Map<String, Object>> requiredAll,
                           List<Map<String, Object>> requiredAny,
                           List<Map<String, Object>> forbiddenAny,
                           List<Map<String, Object>> forbiddenAll) {
        this.requiredAll = wrap(requiredAll);
        this.requiredAny = wrap(requiredAny);
        this.forbiddenAny = wrap(forbiddenAny);
        this.forbiddenAll = wrap(forbiddenAll);
    }

    private static List<Map<String, Object>> wrap(List<Map<String, Object>> source) {
        return source != null ? Collections.unmodifiableList(source) : List.of();
    }

    public boolean isEmpty() {
        return requiredAll.isEmpty() && requiredAny.isEmpty()
                && forbiddenAny.isEmpty() && forbiddenAll.isEmpty();
    }

    public SceneSignature toSceneSignature() {
        SceneSignature.Builder builder = SceneSignature.builder();
        if (!requiredAll.isEmpty()) {
            builder.requireAll(toConditions(requiredAll));
        }
        if (!requiredAny.isEmpty()) {
            builder.requireAny(toConditions(requiredAny));
        }
        if (!forbiddenAny.isEmpty()) {
            builder.forbidAny(toConditions(forbiddenAny));
        }
        if (!forbiddenAll.isEmpty()) {
            builder.forbidAll(toConditions(forbiddenAll));
        }
        return builder.build();
    }

    private static List<SelectorCondition> toConditions(List<Map<String, Object>> configs) {
        List<SelectorCondition> conditions = new ArrayList<>(configs.size());
        for (Map<String, Object> config : configs) {
            conditions.add(SelectorCondition.fromMap(config));
        }
        return Collections.unmodifiableList(conditions);
    }
}
