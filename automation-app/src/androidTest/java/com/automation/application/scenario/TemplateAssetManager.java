package com.automation.application.scenario;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import com.automation.infrastructure.network.AuthService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Resolves template assets referenced by asset_id into base64 payloads on demand.
 */
public final class TemplateAssetManager {

    private static final String TAG = "TemplateAssetManager";

    private final AuthService authService;
    private final OkHttpClient httpClient;
    private final Map<String, String> base64Cache = new ConcurrentHashMap<>();

    public TemplateAssetManager(AuthService authService) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.httpClient = new OkHttpClient.Builder().build();
    }

    public JSONObject resolve(JSONObject payload, String paramType) throws IOException, JSONException {
        if (payload == null) {
            return null;
        }
        JSONObject normalized = new JSONObject(payload.toString());
        String source = normalized.optString("source");
        if (TextUtils.isEmpty(source) && normalized.has("asset_id")) {
            source = "asset";
        }
        if (!"asset".equalsIgnoreCase(source)) {
            return normalized;
        }
        String assetId = normalized.optString("asset_id");
        if (TextUtils.isEmpty(assetId)) {
            throw new IllegalArgumentException("asset 参数缺少 asset_id");
        }
        String base64 = base64Cache.get(assetId);
        if (base64 == null) {
            base64 = downloadAsset(normalized);
            base64Cache.put(assetId, base64);
        }
        normalized.put("source", "base64");
        normalized.put("type", paramType != null ? paramType.toLowerCase() : "file");
        normalized.put("value", base64);
        return normalized;
    }

    public void clearCache() {
        base64Cache.clear();
    }

    private String downloadAsset(JSONObject payload) throws IOException {
        String token = authService.getSavedToken();
        if (TextUtils.isEmpty(token)) {
            throw new IOException("找不到设备令牌，无法下载资产");
        }
        String baseUrl = authService.getServerUrl();
        if (TextUtils.isEmpty(baseUrl)) {
            throw new IOException("找不到服务器地址，无法下载资产");
        }
        String downloadUrl = payload.optString("download_url");
        if (TextUtils.isEmpty(downloadUrl) || !isAbsoluteUrl(downloadUrl)) {
            String path = payload.optString("download_path");
            if (TextUtils.isEmpty(path)) {
                throw new IOException("资产缺少下载地址");
            }
            if (!path.startsWith("http://") && !path.startsWith("https://")) {
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
                downloadUrl = baseUrl + path;
            } else if (!TextUtils.isEmpty(path)) {
                downloadUrl = path;
            }
        }
        Request request = new Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载资产失败: HTTP " + response.code());
            }
            if (response.body() == null) {
                throw new IOException("资产响应为空");
            }
            try (InputStream stream = response.body().byteStream()) {
                return Base64.encodeToString(readStream(stream), Base64.NO_WRAP);
            }
        }
    }

    private byte[] readStream(InputStream stream) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((read = stream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean isAbsoluteUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
