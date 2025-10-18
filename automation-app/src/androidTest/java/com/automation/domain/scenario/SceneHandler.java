package com.automation.domain.scenario;

/**
 * 场景匹配成功后执行的处理接口。
 */
@FunctionalInterface
public interface SceneHandler {
    /**
     * 执行场景逻辑。
     *
     * @param context 当前上下文
     * @return 场景结果
     * @throws Exception 发生异常时抛出
     */
    SceneResult handle(ScenarioContext context) throws Exception;
}
