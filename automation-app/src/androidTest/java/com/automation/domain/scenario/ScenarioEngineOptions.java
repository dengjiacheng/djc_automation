package com.automation.domain.scenario;

/**
 * 场景引擎运行参数。
 * 允许配置初始化场景、超时行为等。
 */
public final class ScenarioEngineOptions {

    private final String initSceneId;
    private final long noMatchTimeoutMs;

    private ScenarioEngineOptions(Builder builder) {
        this.initSceneId = builder.initSceneId;
        this.noMatchTimeoutMs = builder.noMatchTimeoutMs;
    }

    public String initSceneId() {
        return initSceneId;
    }

    public long noMatchTimeoutMs() {
        return noMatchTimeoutMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String initSceneId;
        private long noMatchTimeoutMs = 30_000L;

        private Builder() {
        }

        public Builder initSceneId(String initSceneId) {
            this.initSceneId = initSceneId;
            return this;
        }

        public Builder noMatchTimeoutMs(long timeout) {
            this.noMatchTimeoutMs = timeout;
            return this;
        }

        public ScenarioEngineOptions build() {
            return new ScenarioEngineOptions(this);
        }
    }
}
