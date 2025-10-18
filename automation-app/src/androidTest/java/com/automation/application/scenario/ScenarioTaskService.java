package com.automation.application.scenario;

import android.util.Log;

import com.automation.domain.command.CommandContext;
import com.automation.domain.command.CommandDescriptor;
import com.automation.domain.command.CommandParameter;
import com.automation.domain.command.CommandRegistry;
import com.automation.domain.command.CommandResult;
import com.automation.domain.scenario.script.ScenarioScript;

import org.json.JSONObject;

import java.util.List;
import java.util.Objects;

/**
 * 高层脚本任务服务，负责 orchestrate 参数绑定、执行与能力发布。
 */
public final class ScenarioTaskService {

    private static final String TAG = "ScenarioTaskService";

    private final ScenarioCatalog catalog;
    private final ScenarioParameterBinder parameterBinder;
    private final ScenarioRunCoordinator runCoordinator;

    public ScenarioTaskService(ScenarioCatalog catalog,
                               ScenarioParameterBinder parameterBinder,
                               ScenarioRunCoordinator runCoordinator) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.parameterBinder = Objects.requireNonNull(parameterBinder, "parameterBinder");
        this.runCoordinator = Objects.requireNonNull(runCoordinator, "runCoordinator");
    }

    public CommandResult startTask(CommandContext ctx, JSONObject params) throws Exception {
        ScenarioTaskRequest request = parameterBinder.bind(params);
        return runCoordinator.execute(ctx, request);
    }

    public JSONObject buildStartTaskMetadata() {
        return catalog.buildCatalogMetadata();
    }

    public List<CommandParameter> startTaskParameters() {
        return parameterBinder.baseParameters();
    }

    public void registerScriptCapabilities(CommandRegistry registry) {
        for (String name : catalog.listScriptNames()) {
            ScenarioScript script = catalog.requireScript(name);
            CommandDescriptor descriptor = CommandDescriptor.builder("start_task:" + script.name())
                    .description(script.description().isEmpty()
                            ? ("脚本任务: " + script.name())
                            : script.description())
                    .parameters(parameterBinder.capabilityParameters(script))
                    .metadataSupplier(() -> catalog.buildScriptMetadata(script))
                    .build();
            registry.registerDescriptor(descriptor);
        }
    }
}
