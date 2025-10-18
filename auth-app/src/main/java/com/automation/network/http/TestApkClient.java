package com.automation.network.http;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.Okio;

/**
 * 客户端用于拉取测试 APK 元数据并下载文件。
 */
public class TestApkClient {
    private static final String TAG = "TestApkClient";
    private static final String LATEST_ENDPOINT = "/api/apk/test/latest";

    private final Context context;
    private final String baseUrl;
    private final OkHttpClient httpClient;

    public TestApkClient(@NonNull Context context, @NonNull String serverUrl) {
        this.context = context.getApplicationContext();
        this.baseUrl = normalizeBaseUrl(serverUrl);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 拉取最新的测试 APK 元数据。
     */
    public TestApkMetadata fetchLatest(@NonNull String token) throws TestApkException {
        String url = buildUrl(LATEST_ENDPOINT);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new TestApkException("拉取测试 APK 信息失败: HTTP " + response.code());
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new TestApkException("测试 APK 信息响应体为空");
            }
            String body = responseBody.string();
            try {
                JSONObject json = new JSONObject(body);
                return TestApkMetadata.fromJson(json);
            } catch (JSONException e) {
                throw new TestApkException("解析测试 APK 信息失败: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            throw new TestApkException("网络请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载指定的 APK 资源。
     */
    public File downloadAsset(@NonNull TestApkMetadata.ApkAsset asset, @NonNull String token) throws TestApkException {
        File apkDir = new File(context.getExternalFilesDir(null), "test-apk");
        if (!apkDir.exists() && !apkDir.mkdirs()) {
            throw new TestApkException("无法创建测试 APK 存储目录: " + apkDir.getAbsolutePath());
        }

        File apkFile = new File(apkDir, asset.fileName);
        if (apkFile.exists() && asset.checksumSha256 != null) {
            try {
                if (verifyChecksum(apkFile, asset.checksumSha256)) {
                    Log.i(TAG, "使用已存在的缓存: " + apkFile.getAbsolutePath());
                    return apkFile;
                }
                Log.w(TAG, "缓存的 APK 校验失败，重新下载");
                if (!apkFile.delete()) {
                    Log.w(TAG, "删除无效缓存失败: " + apkFile.getAbsolutePath());
                }
            } catch (IOException e) {
                Log.w(TAG, "验证缓存文件时出错，重新下载", e);
            }
        }

        Request request = new Request.Builder()
                .url(resolveDownloadUrl(asset.downloadUrl))
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new TestApkException("下载 APK 失败: HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new TestApkException("APK 下载内容为空");
            }
            try (BufferedSink sink = Okio.buffer(Okio.sink(apkFile))) {
                sink.writeAll(body.source());
            }
        } catch (IOException e) {
            throw new TestApkException("下载 APK 时出现网络错误: " + e.getMessage(), e);
        }

        if (asset.checksumSha256 != null) {
            try {
                if (!verifyChecksum(apkFile, asset.checksumSha256)) {
                    throw new TestApkException("APK 校验失败，文件可能损坏");
                }
            } catch (IOException e) {
                throw new TestApkException("校验 APK 时失败: " + e.getMessage(), e);
            }
        }

        return apkFile;
    }

    private static String normalizeBaseUrl(String serverUrl) {
        String trimmed = serverUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String buildUrl(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (path.startsWith("/")) {
            return baseUrl + path;
        }
        return baseUrl + "/" + path;
    }

    private String resolveDownloadUrl(String downloadUrl) {
        return buildUrl(downloadUrl);
    }

    private boolean verifyChecksum(File file, String expected) throws IOException {
        String actual = computeSha256(file);
        return expected.equalsIgnoreCase(actual);
    }

    private String computeSha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(new FileInputStream(file), digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // 读取以更新摘要
                }
            }
            byte[] hash = digest.digest();
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("系统不支持 SHA-256 算法", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    public static final class TestApkMetadata {
        public final String version;
        public final long versionCode;
        @Nullable
        public final String createdAt;
        public final ApkAsset app;
        public final ApkAsset test;

        private TestApkMetadata(
                String version,
                long versionCode,
                @Nullable String createdAt,
                ApkAsset app,
                ApkAsset test
        ) {
            this.version = version;
            this.versionCode = versionCode;
            this.createdAt = createdAt;
            this.app = app;
            this.test = test;
        }

        static TestApkMetadata fromJson(JSONObject json) throws JSONException, TestApkException {
            String version = json.optString("version", null);
            long versionCode = json.optLong("version_code", -1);
            if (version == null || versionCode < 0) {
                throw new TestApkException("测试 APK 信息缺少版本字段");
            }
            JSONObject appJson = json.optJSONObject("app");
            JSONObject testJson = json.optJSONObject("test");
            if (appJson == null || testJson == null) {
                throw new TestApkException("测试 APK 信息缺少 app/test 字段");
            }
            ApkAsset appAsset = ApkAsset.fromJson(appJson, "app");
            ApkAsset testAsset = ApkAsset.fromJson(testJson, "test");
            String createdAt = json.optString("created_at", null);
            if (createdAt != null && createdAt.isEmpty()) {
                createdAt = null;
            }
            return new TestApkMetadata(version, versionCode, createdAt, appAsset, testAsset);
        }

        public static final class ApkAsset {
            public final String fileName;
            public final String downloadUrl;
            public final long sizeBytes;
            @Nullable
            public final String checksumSha256;
            @Nullable
            public final String packageName;
            @Nullable
            public final Long versionCode;
            @Nullable
            public final String versionName;

            private ApkAsset(
                    String fileName,
                    String downloadUrl,
                    long sizeBytes,
                    @Nullable String checksumSha256,
                    @Nullable String packageName,
                    @Nullable Long versionCode,
                    @Nullable String versionName
            ) {
                this.fileName = fileName;
                this.downloadUrl = downloadUrl;
                this.sizeBytes = sizeBytes;
                this.checksumSha256 = checksumSha256;
                this.packageName = packageName;
                this.versionCode = versionCode;
                this.versionName = versionName;
            }

            static ApkAsset fromJson(JSONObject json, String label) throws JSONException, TestApkException {
                String fileName = json.optString("file_name", null);
                String downloadUrl = json.optString("download_url", null);
                if (fileName == null || downloadUrl == null) {
                    throw new TestApkException(label + " 信息缺少必须字段");
                }
                long sizeBytes = json.optLong("size_bytes", -1);
                String checksum = json.optString("checksum_sha256", null);
                String packageName = json.optString("package_name", null);
                if (checksum != null && checksum.isEmpty()) {
                    checksum = null;
                }
                if (packageName != null && packageName.isEmpty()) {
                    packageName = null;
                }
                Long versionCode = null;
                if (json.has("version_code")) {
                    try {
                        versionCode = json.isNull("version_code") ? null : json.getLong("version_code");
                    } catch (JSONException ignored) {
                        versionCode = null;
                    }
                }
                String versionName = json.optString("version_name", null);
                if (versionName != null && versionName.isEmpty()) {
                    versionName = null;
                }
                return new ApkAsset(fileName, downloadUrl, sizeBytes, checksum, packageName, versionCode, versionName);
            }
        }
    }

    public static class TestApkException extends Exception {
        public TestApkException(String message) {
            super(message);
        }

        public TestApkException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
