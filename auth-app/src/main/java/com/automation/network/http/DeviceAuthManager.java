package com.automation.network.http;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 设备认证管理器
 * 负责设备登录和 Token 管理
 */
public class DeviceAuthManager {
    private static final String TAG = "DeviceAuthManager";
    private static final String PREF_NAME = "device_auth";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_WS_URL = "ws_url";

    private final Context context;
    private final OkHttpClient httpClient;
    private final String serverUrl;

    public DeviceAuthManager(Context context, String serverUrl) {
        this.context = context.getApplicationContext();
        this.serverUrl = serverUrl;
        this.httpClient = createOkHttpClient();
    }

    /**
     * 创建 OkHttpClient
     */
    private OkHttpClient createOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 设备登录
     */
    public void login(String username, String password, AuthCallback callback) {
        try {
            // 构建请求体
            JSONObject json = new JSONObject();
            json.put("username", username);
            json.put("password", password);
            final String cachedDeviceId = getSavedDeviceId();
            if (cachedDeviceId != null && !cachedDeviceId.isEmpty()) {
                json.put("device_id", cachedDeviceId);
            }

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json"));

            // 构建请求
            Request request = new Request.Builder()
                    .url(serverUrl + "/api/auth/device/login")
                    .post(body)
                    .build();

            // 异步请求
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "登录失败", e);
                    callback.onFailure("网络错误: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        try {
                            JSONObject result = new JSONObject(responseBody);
                            String token = result.getString("access_token");
                            String wsUrl = result.getString("ws_url");
                            String deviceId = result.optString("device_id", null);
                            if (deviceId == null || deviceId.isEmpty()) {
                                deviceId = cachedDeviceId;
                            }

                            // 保存 token, wsUrl, deviceId
                            saveAuth(token, deviceId, wsUrl);

                            Log.i(TAG, "登录成功，获得 device_id: " + deviceId);
                            callback.onSuccess(token, deviceId, wsUrl);
                        } catch (JSONException e) {
                            Log.e(TAG, "解析响应失败", e);
                            callback.onFailure("解析错误: " + e.getMessage());
                        }
                    } else {
                        Log.e(TAG, "登录失败: " + response.code() + " - " + responseBody);
                        callback.onFailure("登录失败: " + response.code());
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "构建请求失败", e);
            callback.onFailure("请求错误: " + e.getMessage());
        }
    }

    /**
     * 保存认证信息到本地
     */
    private void saveAuth(String token, String deviceId, String wsUrl) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_WS_URL, wsUrl);

        // device_id可能为null（登录时），会在WebSocket连接后保存
        if (deviceId != null) {
            editor.putString(KEY_DEVICE_ID, deviceId);
        }

        editor.apply();
    }

    /**
     * 获取保存的 Token
     */
    public String getSavedToken() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TOKEN, null);
    }

    /**
     * 获取保存的设备 ID
     */
    public String getSavedDeviceId() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DEVICE_ID, null);
    }

    /**
     * 获取保存的 WebSocket URL
     */
    public String getSavedWsUrl() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_WS_URL, null);
    }

    /**
     * 清除认证信息
     */
    public void clearAuth() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    /**
     * 认证回调接口
     */
    public interface AuthCallback {
        void onSuccess(String token, String deviceId, String wsUrl);

        void onFailure(String error);
    }
}
