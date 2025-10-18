package com.automation.domain.scenario;

/**
 * 场景执行过程中的事件上报接口，可用于透传日志到 WebSocket。
 * 默认提供 NO_OP 实现，方便调用方按需覆盖。
 */
public interface ScenarioReporter {

    ScenarioReporter NO_OP = new ScenarioReporter() {
    };

    default void onInfo(String message) {
    }

    default void onWarning(String message) {
    }

    default void onError(String message, Throwable error) {
    }

    default void onSceneMatched(String sceneId, String description) {
    }

    default void onSceneConflict(String[] sceneIds) {
    }

    default void onTimeout() {
    }
}
