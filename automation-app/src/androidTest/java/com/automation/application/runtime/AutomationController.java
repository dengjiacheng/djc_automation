package com.automation.application.runtime;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.automation.infrastructure.system.AppManager;
import com.automation.infrastructure.system.ClipboardHelper;
import com.automation.infrastructure.system.ScreenshotHelper;
import com.automation.infrastructure.vision.ImageRecognition;
import com.automation.domain.command.CommandContext;
import com.automation.domain.command.CommandResult;
import com.automation.infrastructure.network.AutomationMessage;
import com.automation.infrastructure.network.AutomationWebSocketClient;
import com.automation.infrastructure.network.AuthService;

import org.json.JSONObject;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.automation.domain.scenario.ScriptRunGuard;

/**
 * Coordinates automation lifecycle: initializes device services, maintains
 * websocket connection, dispatches incoming commands and reports results.
 */
public final class AutomationController implements AutomationWebSocketClient.MessageCallback,
        AutomationWebSocketClient.ConnectionCallback {

    private static final String TAG = "AutomationController";
    private static final String PREFS_NAME = "AutomationConfig";

    private final Context context;
    private final Instrumentation instrumentation;
    private final SharedPreferences preferences;
    private final UiDevice uiDevice;
    private final ScreenshotHelper screenshotHelper;
    private final AppManager appManager;
    private final ClipboardHelper clipboardHelper;
    private final ImageRecognition imageRecognition;
    private final AutomationWebSocketClient webSocketClient;
    private final CommandExecutionEngine commandEngine;
    private AuthService authService;
    private String deviceId;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScriptRunGuard scriptRunGuard = new ScriptRunGuard();
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor(new CommandThreadFactory());
    private final Object taskLock = new Object();
    private final AtomicReference<RunningCommand> currentCommand = new AtomicReference<>();

    public AutomationController() throws Exception {
        this.instrumentation = InstrumentationRegistry.getInstrumentation();
        this.context = instrumentation.getTargetContext().getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.uiDevice = UiDevice.getInstance(instrumentation);
        this.screenshotHelper = new ScreenshotHelper(uiDevice);
        this.appManager = new AppManager(context, uiDevice);
        this.clipboardHelper = new ClipboardHelper(context);
        this.imageRecognition = new ImageRecognition();
        this.webSocketClient = new AutomationWebSocketClient(context);
        this.commandEngine = new CommandExecutionEngine(context, instrumentation.getContext(), uiDevice, appManager,
                clipboardHelper, screenshotHelper, imageRecognition);
        this.webSocketClient.setCapabilitiesProvider(() -> commandEngine.getCapabilitiesJson());
    }

    public void setAuthService(AuthService authService) {
        this.authService = authService;
        this.webSocketClient.setAuthService(authService);
        if (authService != null) {
            String saved = authService.getSavedDeviceId();
            if (saved != null) {
                setDeviceId(saved);
            }
        }
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
        webSocketClient.setDeviceId(deviceId);
        if (authService != null && deviceId != null) {
            authService.saveDeviceId(deviceId);
        }
    }

    public void start(String wsUrl) {
        if (running.get()) {
            Log.w(TAG, "AutomationController 已启动");
            return;
        }
        running.set(true);

        preferences.edit()
                .putString("wsUrl", wsUrl)
                .apply();

        webSocketClient.setMessageCallback(this);
        webSocketClient.setDeviceId(deviceId);
        webSocketClient.connect(wsUrl, this);

        Log.i(TAG, "AutomationController 启动完成，等待指令...");
    }

    public void keepAlive() throws InterruptedException {
        while (running.get()) {
            Thread.sleep(10_000);
        }
    }

    public void stop() {
        running.set(false);
        webSocketClient.disconnect();
        RunningCommand active = currentCommand.getAndSet(null);
        if (active != null) {
            active.cancel();
        }
        commandExecutor.shutdownNow();
    }

    @Override
    public void onConnected() {
        Log.i(TAG, "✓ WebSocket 已连接");
    }

    @Override
    public void onDisconnected() {
        Log.w(TAG, "WebSocket 已断开");
        if (running.get()) {
            webSocketClient.requestReconnect();
        }
    }

    @Override
    public void onError(String error) {
        Log.e(TAG, "WebSocket 错误: " + error);
        if (running.get()) {
            webSocketClient.requestReconnect();
        }
    }

    @Override
    public void onMessage(AutomationMessage message) {
        Log.d(TAG, "收到消息类型: " + message.getType());
    }

    @Override
    public void onCommand(String commandId, String action, JSONObject payload, String userId) {
        String safeCommandId = commandId != null ? commandId : UUID.randomUUID().toString();
        Log.i(TAG, "执行指令: " + action + " (id=" + safeCommandId + ")");

        if ("stop_task".equals(action)) {
            handleStopRequest(safeCommandId, userId);
            return;
        }

        if (!running.get()) {
            sendCommandResult(safeCommandId, false, null, "controller not running", userId);
            return;
        }

        synchronized (taskLock) {
            RunningCommand active = currentCommand.get();
            if (active != null && !active.isCompleted()) {
                Log.w(TAG, "设备忙，拒绝新指令: " + action);
                sendCommandResult(safeCommandId, false, null, "device busy", userId);
                return;
            }

            String guardToken = safeCommandId;
            if (!scriptRunGuard.tryAcquire(guardToken)) {
                Log.w(TAG, "脚本互斥锁占用，拒绝新指令");
                sendCommandResult(safeCommandId, false, null, "device busy", userId);
                return;
            }

            RunningCommand session = new RunningCommand(safeCommandId, action, userId, payload, guardToken);
            try {
                Future<?> future = commandExecutor.submit(() -> executeCommand(session));
                session.attachFuture(future);
                currentCommand.set(session);
            } catch (RejectedExecutionException rex) {
                Log.e(TAG, "指令提交失败: " + action, rex);
                scriptRunGuard.release(guardToken);
                sendCommandResult(safeCommandId, false, null, "executor rejected command", userId);
            }
        }
    }

    public String getSavedWsUrl() {
        return preferences.getString("wsUrl", "");
    }

    private void executeCommand(RunningCommand session) {
        try {
            session.reportProgress("start", "开始执行指令", 0, null);
            CommandResult result = commandEngine.execute(session, session.action, session.payload);
            boolean success = result == null || result.isSuccess();
            Object payload = result != null ? result.payload() : null;
            String rendered = result != null ? result.renderPayload() : null;
            JSONObject progressExtra = toJsonObject(payload);

            if (success) {
                sendCommandResult(session.commandId, true, rendered, null, session.userId);
                Log.i(TAG, "✓ 指令执行成功: " + session.action);
                Log.d(TAG, "Command payload result=" + rendered);
                String finishMessage = result != null && result.message() != null
                        ? result.message()
                        : "指令执行完成";
                session.reportProgress("finish", finishMessage, 100, progressExtra);
            } else {
                String errorMessage = result.message() != null ? result.message() : "指令执行失败";
                Log.w(TAG, "✗ 指令执行失败: " + session.action + " - " + errorMessage);
                Log.d(TAG, "Command failure payload=" + rendered);
                session.reportProgress("error", errorMessage, null, progressExtra);
                sendCommandResult(session.commandId, false, rendered, errorMessage, session.userId);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "指令被中断: " + session.action);
            session.reportProgress("error", "指令被中断", null, null);
            sendCommandResult(session.commandId, false, null, "command interrupted", session.userId);
        } catch (Exception e) {
            Log.e(TAG, "✗ 指令执行失败: " + session.action, e);
            JSONObject extra = new JSONObject();
            try {
                extra.put("exception", e.getClass().getSimpleName());
                extra.put("message", e.getMessage());
            } catch (Exception ignore) {
                // ignore json errors
            }
            session.reportProgress("error", "指令执行失败", null, extra);
            sendCommandResult(session.commandId, false, null, e.getMessage(), session.userId);
        } finally {
            scriptRunGuard.release(session.guardToken);
            clearCurrentCommand(session);
        }
    }

    private JSONObject toJsonObject(Object payload) {
        if (payload == null) {
            return null;
        }
        Object wrapped = JSONObject.wrap(payload);
        if (wrapped instanceof JSONObject json) {
            return json;
        }
        return null;
    }

    private void handleStopRequest(String commandId, String userId) {
        RunningCommand active = currentCommand.get();
        if (active == null || active.isCompleted()) {
            Log.w(TAG, "停止请求但当前无执行中的任务");
            sendCommandResult(commandId, false, null, "no running task", userId);
            return;
        }

        boolean cancelled = active.cancel();
        if (cancelled) {
            Log.i(TAG, "已发送中断信号给任务: " + active.action);
            sendCommandResult(commandId, true, "cancel requested", null, userId);
        } else {
            Log.w(TAG, "取消任务失败，可能已完成");
            sendCommandResult(commandId, false, null, "unable to cancel", userId);
        }
    }

    private void clearCurrentCommand(RunningCommand session) {
        synchronized (taskLock) {
            RunningCommand active = currentCommand.get();
            if (active == session) {
                currentCommand.set(null);
            }
        }
    }

    private void sendCommandResult(String commandId,
            boolean success,
            String result,
            String error,
            String userId) {
        String deviceId = getDeviceId();
        webSocketClient.sendCommandResult(commandId, success, result, error, userId, deviceId);
    }

    private String getDeviceId() {
        if (deviceId != null) {
            return deviceId;
        }
        if (authService != null) {
            String saved = authService.getSavedDeviceId();
            if (saved != null) {
                setDeviceId(saved);
                return saved;
            }
        }
        return null;
    }

    private final class RunningCommand implements CommandContext {
        final String commandId;
        final String action;
        final String userId;
        final JSONObject payload;
        final String guardToken;
        private final AtomicReference<Future<?>> futureRef = new AtomicReference<>();

        final String deviceId;

        RunningCommand(String commandId, String action, String userId, JSONObject payload, String guardToken) {
            this.commandId = Objects.requireNonNull(commandId);
            this.action = Objects.requireNonNull(action);
            this.userId = userId;
            this.payload = payload;
            this.guardToken = guardToken;
            this.deviceId = getDeviceId();
        }

        void attachFuture(Future<?> future) {
            futureRef.set(future);
        }

        boolean isCompleted() {
            Future<?> future = futureRef.get();
            return future == null || future.isDone();
        }

        boolean cancel() {
            Future<?> future = futureRef.get();
            if (future == null) {
                return false;
            }
            return future.cancel(true);
        }

        @Override
        public String commandId() {
            return commandId;
        }

        @Override
        public String action() {
            return action;
        }

        @Override
        public JSONObject params() {
            return payload;
        }

        @Override
        public String userId() {
            return userId;
        }

        @Override
        public String deviceId() {
            return deviceId;
        }

        @Override
        public void reportProgress(String stage, String message, Integer percent, JSONObject extra) {
            webSocketClient.sendCommandProgress(commandId, stage, message, percent, extra, userId, deviceId);
        }

        @Override
        public void reportLog(String level, String message, JSONObject extra) {
            webSocketClient.sendCommandLog(level, message, extra, userId, deviceId);
        }

        com.automation.domain.scenario.ScenarioContext.Builder newScenarioContextBuilder() {
            return commandEngine.newScenarioContextBuilder(this);
        }
    }

    private static final class CommandThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "automation-command-executor");
            thread.setDaemon(true);
            return thread;
        }
    }
}
