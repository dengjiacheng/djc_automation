package com.automation.infrastructure.network;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.automation.infrastructure.system.NetworkInspector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Minimal WebSocket client tailored for the automation runtime.
 */
public final class AutomationWebSocketClient {

    private static final String TAG = "AutomationWebSocket";
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;

    private final Context context;
    private final OkHttpClient httpClient;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebSocket webSocket;
    private boolean connected;
    private String currentUrl;
    private boolean intentionalClose;
    private boolean reconnectScheduled;
    private MessageCallback messageCallback;
    private ConnectionCallback connectionCallback;
    private Runnable heartbeatTask;
    private AuthService authService;
    private Supplier<JSONArray> capabilitiesProvider;
    private String deviceId;
    private final Deque<AutomationMessage> pendingMessages = new ArrayDeque<>();
    private static final int MAX_PENDING_MESSAGES = 200;
    private final Map<String, AutomationMessage> pendingAcks = new LinkedHashMap<>();
    private volatile boolean handshakeCompleted = false;
    private static final String TYPE_SESSION_INIT = "session_init";
    private static final String TYPE_SESSION_READY = "session_ready";
    private static final String TYPE_COMMAND_ACK = "command_ack";
    private static final String TYPE_COMMAND = "command";

    public AutomationWebSocketClient(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    public void setMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
    }

    public void setCapabilitiesProvider(Supplier<JSONArray> provider) {
        this.capabilitiesProvider = provider;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public void connect(String wsUrl, ConnectionCallback callback) {
        if (connected) {
            Log.w(TAG, "已有活跃的 WebSocket 连接");
            return;
        }
        this.currentUrl = wsUrl;
        this.intentionalClose = false;
        this.connectionCallback = callback;

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                connected = true;
                reconnectScheduled = false;
                Log.i(TAG, "WebSocket 连接成功");
                handshakeCompleted = false;
                sendSessionInit();
                startHeartbeat();
                if (connectionCallback != null) {
                    connectionCallback.onConnected();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleIncomingMessage(text);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.i(TAG, "WebSocket 已关闭: " + code + " - " + reason);
                connected = false;
                handshakeCompleted = false;
                stopHeartbeat();
                if (connectionCallback != null) {
                    connectionCallback.onDisconnected();
                }
                if (!intentionalClose) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket 连接失败", t);
                connected = false;
                handshakeCompleted = false;
                stopHeartbeat();
                if (connectionCallback != null) {
                    connectionCallback.onError(t.getMessage());
                }
                if (!intentionalClose) {
                    scheduleReconnect();
                }
            }
        });
    }

    public void disconnect() {
        intentionalClose = true;
        stopHeartbeat();
        if (webSocket != null) {
            webSocket.close(1000, "client shutdown");
            webSocket = null;
        }
        connected = false;
        synchronized (pendingMessages) {
            pendingMessages.clear();
        }
    }

    public void sendMessage(AutomationMessage message) {
        if (message == null) {
            return;
        }

        String type = message.getType();
        boolean isSessionInit = TYPE_SESSION_INIT.equals(type);

        boolean shouldQueue;
        synchronized (pendingMessages) {
            shouldQueue = !connected || webSocket == null || (!handshakeCompleted && !isSessionInit);
            if (shouldQueue) {
                enqueuePendingLocked(message);
            }
        }

        if (!shouldQueue) {
            try {
                webSocket.send(message.toJson());
            } catch (JSONException e) {
                Log.e(TAG, "消息序列化失败", e);
            } catch (IllegalStateException state) {
                Log.w(TAG, "WebSocket 当前不可写，缓存消息: " + message.getType(), state);
                enqueuePending(message);
            }
        }
    }

    public void sendCommandResult(String commandId,
                                  boolean success,
                                  String result,
                                  String errorMessage,
                                  String userId,
                                  String deviceId,
                                  String action) {
        try {
            AutomationMessage message =
                    AutomationMessage.commandResult(commandId, success, result, errorMessage, userId, deviceId, action);
            if (commandId != null) {
                synchronized (pendingAcks) {
                    pendingAcks.put(commandId, message);
                }
            }
            sendMessage(message);
        } catch (JSONException e) {
            Log.e(TAG, "发送指令执行结果失败", e);
        }
    }

    public void sendCommandProgress(String commandId,
                                    String stage,
                                    String message,
                                    Integer percent,
                                    JSONObject extra,
                                    String userId,
                                    String deviceId) {
        try {
            JSONObject data = new JSONObject();
            data.put("command_id", commandId);
            data.put("stage", stage);
            data.put("message", message);
            if (percent != null) {
                data.put("percent", percent);
            }
            data.put("status", "running");
            if (extra != null) {
                data.put("extra", extra);
            }
            data.put("user_id", userId);
            data.put("device_id", deviceId);
            sendMessage(new AutomationMessage("progress", data));
        } catch (JSONException e) {
            Log.e(TAG, "发送指令进度失败", e);
        }
    }

    public void sendCommandLog(String level,
                               String message,
                               JSONObject extra,
                               String userId,
                               String deviceId) {
        try {
            JSONObject payload = AutomationMessage.log(level, message).getData();
            if (extra != null) {
                payload.put("extra", extra);
            }
            if (userId != null) {
                payload.put("user_id", userId);
            }
            if (deviceId != null) {
                payload.put("device_id", deviceId);
            }
            sendMessage(new AutomationMessage("log", payload));
        } catch (JSONException e) {
            Log.e(TAG, "发送日志失败", e);
        }
    }

    private void handleIncomingMessage(String raw) {
        try {
            AutomationMessage message = AutomationMessage.fromJson(raw);
            if (messageCallback != null) {
                messageCallback.onMessage(message);
            }
            if (TYPE_SESSION_READY.equals(message.getType())) {
                handleSessionReady(message.getData());
                onHandshakeCompleted();
            } else if (TYPE_COMMAND.equals(message.getType()) && message.getData() != null) {
                JSONObject data = message.getData();
                String commandId = data.optString("command_id");
                String action = data.optString("action");
                JSONObject params = data.optJSONObject("params");
                String userId = data.optString("user_id", null);
                if (messageCallback != null) {
                    messageCallback.onCommand(commandId, action, params, userId);
                }
            } else if (TYPE_COMMAND_ACK.equals(message.getType())) {
                handleAck(message.getData());
            } else if ("ping".equals(message.getType())) {
                sendMessage(new AutomationMessage("pong", null));
            }
        } catch (JSONException e) {
            Log.e(TAG, "解析消息失败: " + raw, e);
        }
    }

    private void handleSessionReady(JSONObject data) throws JSONException {
        if (data != null && authService != null && data.has("device_id")) {
            String newId = data.optString("device_id", null);
            if (newId != null) {
                authService.saveDeviceId(newId);
                deviceId = newId;
            }
        }
    }

    private void handleAck(JSONObject data) {
        if (data == null) {
            return;
        }
        String commandId = data.optString("command_id", null);
        if (commandId == null) {
            return;
        }
        synchronized (pendingAcks) {
            if (pendingAcks.remove(commandId) != null) {
                Log.d(TAG, "已收到指令 ACK, commandId=" + commandId);
            } else {
                Log.d(TAG, "收到未知 ACK, commandId=" + commandId);
            }
        }
    }

    private void sendSessionInit() {
        try {
            JSONObject data = new JSONObject();
            data.put("device_name", Build.MODEL);
            data.put("device_model", Build.DEVICE);
            data.put("android_version", Build.VERSION.RELEASE);

            String ip = NetworkInspector.getLocalIpAddress(context);
            if (ip != null) {
                data.put("local_ip", ip);
            }
            if (deviceId != null) {
                data.put("device_id", deviceId);
            }
            if (capabilitiesProvider != null) {
                try {
                    JSONArray capabilities = capabilitiesProvider.get();
                    if (capabilities != null && capabilities.length() > 0) {
                        data.put("capabilities", capabilities);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "构建能力列表失败", e);
                }
            }

            sendMessage(AutomationMessage.sessionInit(data));
        } catch (JSONException e) {
            Log.e(TAG, "构建 session 初始化消息失败", e);
        }
    }

    private void onHandshakeCompleted() {
        if (handshakeCompleted) {
            return;
        }
        handshakeCompleted = true;
        flushPendingMessages();
        replayPendingAcks();
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = new Runnable() {
            @Override
            public void run() {
                try {
                    sendMessage(AutomationMessage.heartbeat(80, "unknown", "idle"));
                } catch (JSONException e) {
                    Log.e(TAG, "发送心跳失败", e);
                }
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
            }
        };
        handler.postDelayed(heartbeatTask, HEARTBEAT_INTERVAL_MS);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            handler.removeCallbacks(heartbeatTask);
            heartbeatTask = null;
        }
    }

    private void scheduleReconnect() {
        if (currentUrl == null || intentionalClose || reconnectScheduled) {
            return;
        }
        reconnectScheduled = true;
        handler.postDelayed(() -> {
            reconnectScheduled = false;
            if (intentionalClose) {
                return;
            }
            if (!NetworkInspector.isNetworkAvailable(context)) {
                scheduleReconnect();
                return;
            }
            Log.i(TAG, "尝试重新连接 WebSocket");
            connect(currentUrl, connectionCallback);
        }, 5_000);
    }

    public void requestReconnect() {
        if (currentUrl == null) {
            return;
        }
        intentionalClose = false;
        scheduleReconnect();
    }

    public interface MessageCallback {
        void onMessage(AutomationMessage message);
        void onCommand(String commandId, String action, JSONObject payload, String userId);
    }

    public interface ConnectionCallback {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    private void enqueuePending(AutomationMessage message) {
        synchronized (pendingMessages) {
            enqueuePendingLocked(message);
        }
    }

    private void enqueuePendingLocked(AutomationMessage message) {
        if (message == null) {
            return;
        }
        if (pendingMessages.size() >= MAX_PENDING_MESSAGES) {
            pendingMessages.pollFirst();
        }
        pendingMessages.addLast(message);
    }

    private void flushPendingMessages() {
        while (true) {
            AutomationMessage message;
            synchronized (pendingMessages) {
                if (!connected || webSocket == null || !handshakeCompleted) {
                    return;
                }
                message = pendingMessages.pollFirst();
            }
            if (message == null) {
                return;
            }
            try {
                webSocket.send(message.toJson());
            } catch (JSONException e) {
                Log.e(TAG, "重发缓存消息失败", e);
            } catch (IllegalStateException state) {
                Log.w(TAG, "重发缓存消息时连接不可用，重新入队", state);
                enqueuePending(message);
                return;
            }
        }
    }

    private void replayPendingAcks() {
        Map<String, AutomationMessage> snapshot;
        synchronized (pendingAcks) {
            snapshot = new LinkedHashMap<>(pendingAcks);
        }
        for (AutomationMessage message : snapshot.values()) {
            sendMessage(message);
        }
    }
}
