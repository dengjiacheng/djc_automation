package com.automation.domain.scenario;

import androidx.annotation.Nullable;

/**
 * 场景执行结果封装。
 */
public final class ScenarioRunResult {

    private final ScenarioRunStatus status;
    private final String lastSceneId;
    private final Throwable error;

    private ScenarioRunResult(ScenarioRunStatus status, String lastSceneId, Throwable error) {
        this.status = status;
        this.lastSceneId = lastSceneId;
        this.error = error;
    }

    public static ScenarioRunResult success(String sceneId) {
        return new ScenarioRunResult(ScenarioRunStatus.SUCCESS, sceneId, null);
    }

    public static ScenarioRunResult stopped(String sceneId) {
        return new ScenarioRunResult(ScenarioRunStatus.STOPPED, sceneId, null);
    }

    public static ScenarioRunResult failed(String sceneId, Throwable error) {
        return new ScenarioRunResult(ScenarioRunStatus.FAILED, sceneId, error);
    }

    public static ScenarioRunResult timeout(String sceneId) {
        return new ScenarioRunResult(ScenarioRunStatus.TIMEOUT, sceneId, null);
    }

    public static ScenarioRunResult empty() {
        return new ScenarioRunResult(ScenarioRunStatus.EMPTY, null, null);
    }

    public ScenarioRunStatus status() {
        return status;
    }

    @Nullable
    public String lastSceneId() {
        return lastSceneId;
    }

    @Nullable
    public Throwable error() {
        return error;
    }
}
