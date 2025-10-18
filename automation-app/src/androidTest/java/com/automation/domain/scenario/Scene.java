package com.automation.domain.scenario;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 表示单个自动化场景的定义。
 */
public final class Scene {

    private final String id;
    private final String description;
    private final SceneSignature signature;
    private final SceneHandler handler;
    private final List<String> pruneScenes;

    private Scene(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.description = builder.description != null ? builder.description : "";
        this.signature = builder.signature;
        this.handler = Objects.requireNonNull(builder.handler, "handler");
        this.pruneScenes = List.copyOf(builder.pruneScenes);
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public SceneSignature signature() {
        return signature;
    }

    public SceneHandler handler() {
        return handler;
    }

    public List<String> pruneScenes() {
        return pruneScenes;
    }

    public boolean isSignatureLess() {
        return signature == null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String description;
        private SceneSignature signature;
        private SceneHandler handler;
        private List<String> pruneScenes = Collections.emptyList();

        private Builder() {
        }

        public Builder id(String value) {
            this.id = value;
            return this;
        }

        public Builder description(String value) {
            this.description = value;
            return this;
        }

        public Builder signature(SceneSignature value) {
            this.signature = value;
            return this;
        }

        public Builder handler(SceneHandler value) {
            this.handler = value;
            return this;
        }

        public Builder pruneScenes(List<String> value) {
            this.pruneScenes = value != null ? value : Collections.emptyList();
            return this;
        }

        public Scene build() {
            return new Scene(this);
        }
    }
}
