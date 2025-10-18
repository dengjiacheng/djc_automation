package com.automation.domain.scenario;

/**
 * 场景执行整体状态。
 */
public enum ScenarioRunStatus {
    /**
     * 全部流程成功结束。
     */
    SUCCESS,

    /**
     * 主动停止，流程未完成。
     */
    STOPPED,

    /**
     * 执行中出现错误。
     */
    FAILED,

    /**
     * 未匹配任何场景或超出超时时间。
     */
    TIMEOUT,

    /**
     * 没有可执行的场景。
     */
    EMPTY
}
