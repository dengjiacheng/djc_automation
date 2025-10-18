package com.automation.application.runtime;

import android.content.Context;

import androidx.test.uiautomator.UiDevice;

import com.automation.infrastructure.system.AppManager;
import com.automation.infrastructure.system.ClipboardHelper;
import com.automation.infrastructure.system.ScreenshotHelper;
import com.automation.infrastructure.vision.ImageRecognition;
import com.automation.domain.command.CommandBus;
import com.automation.domain.command.CommandContext;
import com.automation.domain.command.CommandModule;
import com.automation.domain.command.CommandRegistry;
import com.automation.domain.command.CommandResult;
import com.automation.domain.command.CommandInterceptor;
import com.automation.application.runtime.interceptors.CommandLoggingInterceptor;
import com.automation.domain.command.ScenarioContextFactory;
import com.automation.application.runtime.modules.AppCommandModule;
import com.automation.application.runtime.modules.ClipboardCommandModule;
import com.automation.application.runtime.modules.DeviceInteractionModule;
import com.automation.application.runtime.modules.DiagnosticsCommandModule;
import com.automation.application.runtime.modules.ScenarioCommandModule;
import com.automation.application.runtime.modules.TextInputModule;
import com.automation.application.runtime.modules.VisionCommandModule;
import com.automation.application.scenario.ScenarioCatalog;
import com.automation.application.scenario.ScenarioParameterBinder;
import com.automation.application.scenario.ScenarioRunCoordinator;
import com.automation.application.scenario.ScenarioTaskService;
import com.automation.domain.scenario.ScenarioContext;
import com.automation.domain.scenario.ScriptRunGuard;
import com.automation.domain.scenario.ScenarioRunner;
import com.automation.domain.scenario.device.DeviceActions;
import com.automation.domain.scenario.script.AssetScriptRepository;
import com.automation.domain.scenario.script.ScriptHandlerRegistry;
import com.automation.domain.scenario.script.ScriptRepository;
import com.automation.domain.scenario.vision.VisionToolkit;
import com.automation.feature.scripts.dhgate.DhgateOrderV2Handlers;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 新的指令执行引擎，负责模块注册与调度。
 */
public final class CommandExecutionEngine {

    private final Context appContext;
    private final Context assetContext;
    private final UiDevice uiDevice;
    private final AppManager appManager;
    private final ClipboardHelper clipboardHelper;
    private final ScreenshotHelper screenshotHelper;
    private final ImageRecognition imageRecognition;
    private final DeviceActions deviceActions;
    private final VisionToolkit visionToolkit;
    private final ScriptRepository scriptRepository;
    private final ScriptHandlerRegistry scriptHandlerRegistry;
    private final ScenarioRunner scenarioRunner;
    private final ScenarioCatalog scenarioCatalog;
    private final ScenarioParameterBinder scenarioParameterBinder;
    private final ScenarioRunCoordinator scenarioRunCoordinator;
    private final ScenarioTaskService scenarioTaskService;
    private final CommandBus commandBus = new CommandBus();
    private final CommandRegistry commandRegistry = new CommandRegistry(commandBus);

    public CommandExecutionEngine(Context appContext,
                                  Context instrumentationContext,
                                  UiDevice uiDevice,
                                  AppManager appManager,
                                  ClipboardHelper clipboardHelper,
                                  ScreenshotHelper screenshotHelper,
                                  ImageRecognition imageRecognition) {
        this.appContext = appContext.getApplicationContext();
        this.assetContext = instrumentationContext != null ? instrumentationContext : this.appContext;
        this.uiDevice = uiDevice;
        this.appManager = appManager;
        this.clipboardHelper = clipboardHelper;
        this.screenshotHelper = screenshotHelper;
        this.imageRecognition = imageRecognition;
        this.deviceActions = new DeviceActions(uiDevice);
        this.visionToolkit = new VisionToolkit(this.appContext, screenshotHelper, imageRecognition);
        this.scriptRepository = new AssetScriptRepository(this.assetContext);
        this.scriptHandlerRegistry = new ScriptHandlerRegistry(List.of(new DhgateOrderV2Handlers()));
        this.scenarioRunner = new ScenarioRunner(new ScriptRunGuard());
        ScenarioContextFactory contextFactory = this::newScenarioContextBuilder;
        this.scenarioCatalog = new ScenarioCatalog(scriptRepository);
        this.scenarioParameterBinder = new ScenarioParameterBinder(scenarioCatalog);
        this.scenarioRunCoordinator = new ScenarioRunCoordinator(scenarioRunner, scriptHandlerRegistry, contextFactory);
        this.scenarioTaskService = new ScenarioTaskService(
                scenarioCatalog,
                scenarioParameterBinder,
                scenarioRunCoordinator
        );
        registerInterceptors();
        registerModules();
    }

    public CommandResult execute(CommandContext context, String action, JSONObject params) throws Exception {
        return commandBus.dispatch(context, action, params);
    }

    public JSONArray getCapabilitiesJson() {
        return commandRegistry.capabilitiesAsJson();
    }

    ScenarioContext.Builder newScenarioContextBuilder(CommandContext commandContext) {
        return ScenarioContext.builder()
                .appContext(appContext)
                .uiDevice(uiDevice)
                .deviceActions(deviceActions)
                .visionToolkit(visionToolkit);
    }

    private void registerModules() {
        List<CommandModule> modules = List.of(
                new DeviceInteractionModule(uiDevice),
                new AppCommandModule(appManager),
                new ClipboardCommandModule(clipboardHelper),
                new DiagnosticsCommandModule(appContext, uiDevice, screenshotHelper),
                new VisionCommandModule(imageRecognition, visionToolkit),
                new TextInputModule(appContext, uiDevice),
                new ScenarioCommandModule(scenarioTaskService)
        );
        for (CommandModule module : modules) {
            module.register(commandRegistry);
        }
    }

    private void registerInterceptors() {
        commandRegistry.addInterceptor(new CommandLoggingInterceptor());
    }

    public void addInterceptor(CommandInterceptor interceptor) {
        commandRegistry.addInterceptor(interceptor);
    }
}
