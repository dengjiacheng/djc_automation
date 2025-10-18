package com.automation.domain.command;

import com.automation.domain.scenario.ScenarioContext;

/**
 * 提供基于指令上下文创建场景上下文的工厂。
 */
public interface ScenarioContextFactory {
    ScenarioContext.Builder create(CommandContext commandContext);
}
