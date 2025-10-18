package com.automation.domain.scenario;

/**
 * 场景处理结果，用于指导引擎后续行为。
 */
public enum SceneResult {
    /**
     * 场景处理完毕，继续后续场景。
     */
    CONTINUE,

    /**
     * 全部流程成功结束，停止引擎。
     */
    SUCCESS,

    /**
     * 需要立即停止流程，但不视为成功。
     */
    STOP,

    /**
     * 场景处理失败。
     */
    ERROR
}
