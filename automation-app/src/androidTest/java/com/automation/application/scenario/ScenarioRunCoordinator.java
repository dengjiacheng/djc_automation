package com.automation.application.scenario;

import android.util.Log;

import com.automation.domain.command.CommandContext;
import com.automation.domain.command.CommandResult;
import com.automation.domain.command.ScenarioContextFactory;
import com.automation.domain.scenario.CommandScenarioReporter;
import com.automation.domain.scenario.CompositeScenarioReporter;
import com.automation.domain.scenario.Scene;
import com.automation.domain.scenario.ScenarioContext;
import com.automation.domain.scenario.ScenarioEngineOptions;
import com.automation.domain.scenario.ScenarioRunResult;
import com.automation.domain.scenario.ScenarioRunner;
import com.automation.domain.scenario.SceneHandler;
import com.automation.domain.scenario.script.SceneConfig;
import com.automation.domain.scenario.script.ScriptHandlerRegistry;
import com.automation.domain.scenario.script.ScenarioScript;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * 将脚本请求调度至场景引擎，并返回统一的 CommandResult。
 */
public final class ScenarioRunCoordinator {

    private static final String TAG = "ScenarioRunCoord";

    private final ScenarioRunner scenarioRunner;
    private final ScriptHandlerRegistry handlerRegistry;
    private final ScenarioContextFactory contextFactory;

    public ScenarioRunCoordinator(ScenarioRunner scenarioRunner,
            ScriptHandlerRegistry handlerRegistry,
            ScenarioContextFactory contextFactory) {
        this.scenarioRunner = Objects.requireNonNull(scenarioRunner, "scenarioRunner");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
    }

    public CommandResult execute(CommandContext commandContext, ScenarioTaskRequest request) throws JSONException {
        ScenarioTelemetryCollector telemetry = new ScenarioTelemetryCollector();
        ScenarioContext scenarioContext = buildScenarioContext(commandContext, request, telemetry);
        List<Scene> scenes = materializeScenes(request.script());
        ScenarioEngineOptions options = ScenarioEngineOptions.builder()
                .initSceneId(request.script().initSceneId())
                .build();
        commandContext.reportProgress("task.start", "开始执行任务: " + request.taskName(), 0, null);
        ScenarioRunResult runResult = scenarioRunner.run(request.taskName(), scenes, scenarioContext, options);
        telemetry.finalizeResult(runResult);

        JSONObject summary = buildSummary(request, runResult, telemetry);
        CommandResult result = switch (runResult.status()) {
            case SUCCESS -> CommandResult.success(summary, "任务已完成");
            case STOPPED -> CommandResult.failure(summary, "任务被停止");
            case TIMEOUT -> CommandResult.failure(summary, "任务超时");
            case FAILED -> CommandResult.failure(summary, "任务失败");
            case EMPTY -> CommandResult.failure(summary, "未加载到任何场景");
            default -> CommandResult.failure(summary, "未知执行状态");
        };
        Log.d(TAG, "Scenario run finished with status=" + runResult.status()
                + ", lastScene=" + runResult.lastSceneId()
                + ", error=" + runResult.error());

        switch (runResult.status()) {
            case SUCCESS -> commandContext.reportProgress("task.finish", "任务已完成", 100, summary);
            case STOPPED -> commandContext.reportProgress("task.stop", "任务被停止", null, summary);
            case TIMEOUT -> commandContext.reportProgress("task.timeout", "任务超时", null, summary);
            case FAILED, EMPTY -> commandContext.reportProgress("task.error",
                    result.message() != null ? result.message() : "任务失败", null, summary);
            default -> {
            }
        }
        return result;
    }

    private ScenarioContext buildScenarioContext(CommandContext commandContext,
            ScenarioTaskRequest request,
            ScenarioTelemetryCollector telemetry) {
        CompositeScenarioReporter reporter = new CompositeScenarioReporter(
                new CommandScenarioReporter(commandContext),
                telemetry);

        ScenarioContext.Builder builder = contextFactory.create(commandContext)
                .data(new HashMap<>(request.contextData()))
                .reporter(reporter);
        return builder.build();
    }

    private List<Scene> materializeScenes(ScenarioScript script) {
        List<Scene> scenes = new ArrayList<>(script.scenes().size());
        for (SceneConfig config : script.scenes()) {
            SceneHandler handler = handlerRegistry.resolve(script.name(), config.handlerName());
            scenes.add(config.toScene(handler));
        }
        return scenes;
    }

    private JSONObject buildSummary(ScenarioTaskRequest request,
            ScenarioRunResult runResult,
            ScenarioTelemetryCollector telemetry) throws JSONException {
        JSONObject summary = new JSONObject();
        summary.put("task_name", request.taskName());
        summary.put("script", request.script().name());
        summary.put("status", runResult.status().name().toLowerCase());
        if (runResult.lastSceneId() != null) {
            summary.put("last_scene", runResult.lastSceneId());
        }
        if (runResult.error() != null) {
            summary.put("error", runResult.error().toString());
        }
        JSONObject configSnapshot = request.normalizedConfig();
        if (configSnapshot.length() > 0) {
            summary.put("config", configSnapshot);
        }
        summary.put("metrics", telemetry.toJson());
        return summary;
    }
}
