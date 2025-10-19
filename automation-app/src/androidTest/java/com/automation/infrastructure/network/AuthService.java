package com.automation.infrastructure.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Handles device authentication against the automation backend.
 * Persists the latest token / websocket url for reuse across sessions.
 */
public final class AuthService {

    private static final String TAG = "AuthService";
    private static final String PREF_NAME = "device_auth";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_WS_URL = "ws_url";

    private final Context context;
    private final String serverUrl;
    private final OkHttpClient httpClient;

    public interface AuthCallback {
        void onSuccess(String token, String deviceId, String wsUrl);

        void onError(String message);
    }

    public AuthService(Context context, String serverUrl) {
        this.context = context.getApplicationContext();
        this.serverUrl = serverUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public void login(String username, String password, AuthCallback callback) {
        try {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("username", username);
            bodyJson.put("password", password);

            RequestBody body = RequestBody.create(
                    bodyJson.toString(),
                    MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(serverUrl + "/api/auth/login")
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "登录失败", e);
                    callback.onError("网络错误: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        try {
                            JSONObject result = new JSONObject(responseBody);
                            String token = result.getString("access_token");
                            String deviceId = result.getString("device_id");
                            String wsUrl = result.getString("ws_url");
                            saveAuth(token, deviceId, wsUrl);
                            callback.onSuccess(token, deviceId, wsUrl);
                        } catch (JSONException e) {
                            Log.e(TAG, "解析响应失败", e);
                            callback.onError("解析错误: " + e.getMessage());
                        }
                    } else {
                        Log.e(TAG, "登录失败: " + response.code() + " - " + responseBody);
                        callback.onError("登录失败: " + response.code());
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "构建请求失败", e);
            callback.onError("请求错误: " + e.getMessage());
        }
    }

    public void saveDeviceId(String deviceId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_DEVICE_ID, deviceId);
        editor.apply();
    }

    public String getSavedDeviceId() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_ID, null);
    }

    public String getSavedToken() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TOKEN, null);
    }

    public String getSavedWsUrl() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_WS_URL, null);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    private void saveAuth(String token, String deviceId, String wsUrl) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_WS_URL, wsUrl);

        if (deviceId != null) {
            editor.putString(KEY_DEVICE_ID, deviceId);
        }

        editor.apply();
    }
}
