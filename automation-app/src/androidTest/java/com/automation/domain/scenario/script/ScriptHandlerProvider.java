package com.automation.domain.scenario.script;

import com.automation.domain.scenario.SceneHandler;

/**
 * 按脚本提供处理器的工厂接口。
 */
public interface ScriptHandlerProvider {

    /**
     * 当前工厂是否负责该脚本。
     */
    boolean supports(String scriptName);

    /**
     * 根据 handler 名称获取具体实现。
     */
    SceneHandler resolve(String handlerName);
}
