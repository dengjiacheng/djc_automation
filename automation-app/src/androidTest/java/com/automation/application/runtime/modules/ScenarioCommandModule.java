package com.automation.application.runtime.modules;

import com.automation.domain.command.CommandDescriptor;
import com.automation.domain.command.CommandModule;
import com.automation.domain.command.CommandRegistry;
import com.automation.application.scenario.ScenarioTaskService;

/**
 * 场景任务指令模块，委托 {@link ScenarioTaskService} 完成实际执行与声明。
 */
public final class ScenarioCommandModule implements CommandModule {

    private final ScenarioTaskService taskService;

    public ScenarioCommandModule(ScenarioTaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public void register(CommandRegistry registry) {
        CommandDescriptor descriptor = CommandDescriptor.builder("start_task")
                .description("启动脚本任务")
                .parameters(taskService.startTaskParameters())
                .metadataSupplier(taskService::buildStartTaskMetadata)
                .build();
        registry.register(descriptor, taskService::startTask);
        taskService.registerScriptCapabilities(registry);
    }
}
