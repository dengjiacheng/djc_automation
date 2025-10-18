package com.automation.domain.scenario.script;

import com.automation.domain.scenario.Scene;
import com.automation.domain.scenario.SceneHandler;
import com.automation.domain.scenario.SceneResult;
import com.automation.domain.scenario.SceneSignature;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 单个场景的配置数据。
 */
public final class SceneConfig {

    private final String id;
    private final String description;
    private final SignatureConfig signature;
    private final String handlerName;
    private final List<String> pruneScenes;

    public SceneConfig(String id,
                       String description,
                       SignatureConfig signature,
                       String handlerName,
                       List<String> pruneScenes) {
        this.id = Objects.requireNonNull(id, "id");
        this.description = description != null ? description : "";
        this.signature = signature;
        this.handlerName = handlerName;
        this.pruneScenes = pruneScenes != null
                ? Collections.unmodifiableList(pruneScenes)
                : List.of();
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public SignatureConfig signature() {
        return signature;
    }

    public String handlerName() {
        return handlerName;
    }

    public List<String> pruneScenes() {
        return pruneScenes;
    }

    public Scene toScene(SceneHandler handler) {
        SceneSignature signatureInstance = null;
        if (signature != null && !signature.isEmpty()) {
            signatureInstance = signature.toSceneSignature();
        }
        SceneHandler targetHandler = handler != null
                ? handler
                : context -> SceneResult.CONTINUE;
        return Scene.builder()
                .id(id)
                .description(description)
                .signature(signatureInstance)
                .handler(targetHandler)
                .pruneScenes(pruneScenes)
                .build();
    }
}
