package com.automation.auth;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.PackageInfoCompat;

import com.automation.auth.databinding.ActivityAutomationControlBinding;
import com.automation.network.http.DeviceAuthManager;
import com.automation.network.http.TestApkClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 认证与自动化入口：
 * 1) 登录服务器换取 WebSocket 地址
 * 2) 启停自动化任务
 */
public class AutomationControlActivity extends AppCompatActivity {
    private static final String TAG = "AutomationControl";
    private static final String PREFS_NAME = "AutomationConfig";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_MAIN_APK_CHECKSUM = "main_apk_checksum";
    private static final String KEY_TEST_APK_CHECKSUM = "test_apk_checksum";

    private static final String DEFAULT_SERVER_URL = "http://120.26.211.209";
    private static final String DEFAULT_USERNAME = "device_android_001";
    private static final String DEFAULT_PASSWORD = "password123";

    private static final String TARGET_PACKAGE = "com.automation";
    private static final String TEST_PACKAGE = "com.automation.test";
    private static final String TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner";
    private static final String TEST_SUITE = "com.automation.bootstrap.instrumentation.AutomationTestSuite";

    private ActivityAutomationControlBinding binding;
    private SharedPreferences preferences;
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean operationInProgress = false;
    private volatile boolean automationRunning = false;
    private volatile String activeTargetPackage = TARGET_PACKAGE;
    private volatile String activeTestPackage = TEST_PACKAGE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAutomationControlBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_dashboard);
        }

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefillFields();
        updateActionButtons();

        binding.buttonLaunch.setOnClickListener(v -> onLaunchClicked());
        binding.buttonStop.setOnClickListener(v -> onStopClicked());
    }

    private void prefillFields() {
        binding.editServerUrl.setText(preferences.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL));
        binding.editUsername.setText(preferences.getString(KEY_USERNAME, DEFAULT_USERNAME));
        binding.editPassword.setText(preferences.getString(KEY_PASSWORD, DEFAULT_PASSWORD));
    }

    private void onLaunchClicked() {
        final String url = requireText(binding.editServerUrl.getText());
        final String username = requireText(binding.editUsername.getText());
        final String password = requireText(binding.editPassword.getText());

        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            appendLog("❌ 请填写完整信息\n");
            return;
        }

        clearLog();
        setOperationInProgress(true);
        appendLog("⏳ 正在连接服务器...\n");
        persistCredentials(url, username, password);

        DeviceAuthManager authManager = new DeviceAuthManager(this, url);
        authManager.login(username, password, new DeviceAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String token, String deviceId, String wsUrl) {
                mainHandler.post(() -> {
                    appendLog("✓ 认证成功，正在准备运行环境\n\n");
                    runAsync(() -> {
                        if (!ensureAutomationBundleReady(url, token)) {
                            automationRunning = false;
                            setOperationInProgress(false);
                            return;
                        }
                        startAutomation(wsUrl, deviceId);
                    });
                });
            }

            @Override
            public void onFailure(String error) {
                mainHandler.post(() -> {
                    appendLog("✗ 认证失败: " + error + "\n");
                    automationRunning = false;
                    setOperationInProgress(false);
                });
            }
        });
    }

    private void onStopClicked() {
        if (operationInProgress || !automationRunning) {
            return;
        }
        appendLog("\n⏳ 正在停止自动化服务...\n");
        setOperationInProgress(true);
        runAsync(this::stopAutomation);
    }

    private boolean ensureAutomationBundleReady(@NonNull String serverUrl, @NonNull String token) {
        appendLog("第0步: 检查自动化运行环境...\n");
        TestApkClient testApkClient = new TestApkClient(this, serverUrl);
        try {
            TestApkClient.TestApkMetadata metadata = testApkClient.fetchLatest(token);
            appendLog("✓ 已获取服务器资源清单\n");

            String mainPackage = metadata.app.packageName != null ? metadata.app.packageName : TARGET_PACKAGE;
            String testPackage = metadata.test.packageName != null ? metadata.test.packageName : TEST_PACKAGE;
            activeTargetPackage = mainPackage;
            activeTestPackage = testPackage;

            long currentTargetVersion = getInstalledVersionCode(mainPackage);
            long currentSupportPresence = getInstalledVersionCode(testPackage);
            boolean mainInstalled = currentTargetVersion >= 0;
            boolean supportInstalled = currentSupportPresence >= 0;

            appendLog("环境检查：主服务"
                    + (mainInstalled ? "已安装" : "待部署")
                    + " / 辅助服务"
                    + (supportInstalled ? "已安装" : "待部署")
                    + "\n");

            String remoteMainChecksum = metadata.app.checksumSha256;
            String remoteTestChecksum = metadata.test.checksumSha256;
            String storedMainChecksum = preferences.getString(KEY_MAIN_APK_CHECKSUM, null);
            String storedTestChecksum = preferences.getString(KEY_TEST_APK_CHECKSUM, null);

            if (!mainInstalled && !TextUtils.isEmpty(storedMainChecksum)) {
                preferences.edit().remove(KEY_MAIN_APK_CHECKSUM).apply();
                storedMainChecksum = null;
            }
            if (!supportInstalled && !TextUtils.isEmpty(storedTestChecksum)) {
                preferences.edit().remove(KEY_TEST_APK_CHECKSUM).apply();
                storedTestChecksum = null;
            }

            boolean needsMainUpdate;
            if (!mainInstalled) {
                needsMainUpdate = true;
            } else if (!TextUtils.isEmpty(remoteMainChecksum)) {
                needsMainUpdate = TextUtils.isEmpty(storedMainChecksum)
                        || !remoteMainChecksum.equalsIgnoreCase(storedMainChecksum);
            } else {
                needsMainUpdate = TextUtils.isEmpty(storedMainChecksum);
            }

            boolean needsTestUpdate;
            if (!supportInstalled) {
                needsTestUpdate = true;
            } else if (!TextUtils.isEmpty(remoteTestChecksum)) {
                needsTestUpdate = TextUtils.isEmpty(storedTestChecksum)
                        || !remoteTestChecksum.equalsIgnoreCase(storedTestChecksum);
            } else {
                needsTestUpdate = TextUtils.isEmpty(storedTestChecksum);
            }

            if (!needsMainUpdate && !needsTestUpdate) {
                appendLog("✓ 运行环境已满足要求\n\n");
                saveInstalledChecksums(remoteMainChecksum, remoteTestChecksum);
                return true;
            }

            File mainApkFile = null;
            File supportApkFile = null;

            if (needsMainUpdate) {
                appendLog("⏬ 正在获取主服务组件...\n");
                mainApkFile = testApkClient.downloadAsset(metadata.app, token);
                appendLog("✓ 主服务组件就绪\n");
            }

            if (needsTestUpdate) {
                appendLog("⏬ 正在获取辅助服务组件...\n");
                supportApkFile = testApkClient.downloadAsset(metadata.test, token);
                appendLog("✓ 辅助服务组件就绪\n");
            }

            if (needsTestUpdate && currentSupportPresence >= 0) {
                appendLog("⏳ 正在替换旧辅助服务组件...\n");
                ShellResult uninstallTest = runShell("pm uninstall " + testPackage, true, false);
                if (uninstallTest.isSuccess()) {
                    appendLog("✓ 旧辅助服务组件已移除\n");
                } else {
                    appendLog("⚠️ 辅助服务组件卸载未完全成功，将继续覆盖安装\n");
                }
            }

            if (needsMainUpdate && currentTargetVersion >= 0) {
                appendLog("⏳ 正在替换旧主服务组件...\n");
                ShellResult uninstallMain = runShell("pm uninstall " + mainPackage, true, false);
                if (uninstallMain.isSuccess()) {
                    appendLog("✓ 旧主服务组件已移除\n");
                } else {
                    appendLog("⚠️ 主服务组件卸载未完全成功，将继续覆盖安装\n");
                }
            }

            if (needsMainUpdate && mainApkFile != null) {
                appendLog("⏳ 正在部署主服务组件...\n");
                ShellResult installMain = runShell(
                        "pm install -r '" + mainApkFile.getAbsolutePath() + "'",
                        true,
                        false);
                if (!installMain.isSuccess()) {
                    appendLog("✗ 主服务组件部署失败\n");
                    return false;
                }
                appendLog("✓ 主服务组件部署完成\n");
            }

            if (needsTestUpdate && supportApkFile != null) {
                appendLog("⏳ 正在部署辅助服务组件...\n");
                ShellResult installTest = runShell(
                        "pm install -r '" + supportApkFile.getAbsolutePath() + "'",
                        true,
                        false);
                if (!installTest.isSuccess()) {
                    appendLog("✗ 辅助服务组件部署失败\n");
                    return false;
                }
                appendLog("✓ 辅助服务组件部署完成\n");
            }

            long verifiedMainVersion = getInstalledVersionCode(mainPackage);
            if (verifiedMainVersion < 0) {
                appendLog("⚠️ 未检测到主服务组件，请检查设备\n");
                return false;
            }
            long supportPresence = getInstalledVersionCode(testPackage);
            if (supportPresence < 0) {
                appendLog("⚠️ 未检测到辅助服务组件，请检查设备\n");
                return false;
            }
            saveInstalledChecksums(remoteMainChecksum, remoteTestChecksum);
            appendLog("✓ 自动化运行环境已准备就绪\n\n");
            return true;
        } catch (TestApkClient.TestApkException e) {
            appendLog("✗ 准备自动化套件失败，请稍后重试\n");
            Log.e(TAG, "拉取或下载自动化套件失败", e);
            return false;
        } catch (IOException e) {
            appendLog("✗ 部署自动化套件时发生错误\n");
            Log.e(TAG, "安装自动化套件时发生 IO 异常", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            appendLog("✗ 操作被中断\n");
            Log.e(TAG, "安装自动化套件时线程被中断", e);
            return false;
        }
    }

    private long getInstalledVersionCode(@NonNull String packageName) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            return PackageInfoCompat.getLongVersionCode(info);
        } catch (PackageManager.NameNotFoundException e) {
            return -1L;
        }
    }

    private void startAutomation(@NonNull String wsUrl, @NonNull String deviceId) {
        appendLog("⏳ 正在启动自动化服务...\n");

        try {
            appendLog("第1步: 清理旧进程...\n");
            boolean stoppedAny = false;
            stoppedAny |= forceStopPackage(activeTestPackage);
            stoppedAny |= forceStopPackage(activeTargetPackage);

            if (!stoppedAny) {
                appendLog("✗ 清理旧进程失败\n");
                automationRunning = false;
                setOperationInProgress(false);
                return;
            }

            appendLog("✓ 旧进程已清理\n\n");
            appendLog("第2步: 启动自动化服务...\n");

            String instrumentCmd = String.format(Locale.US,
                    "am instrument -w -r -e class %s -e wsUrl %s -e deviceId %s %s/%s",
                    TEST_SUITE, wsUrl, deviceId, activeTestPackage, TEST_RUNNER);

            Process process = Runtime.getRuntime().exec(new String[] { "su", "-c", instrumentCmd });
            Thread stdoutThread = drainStream(process.getInputStream(), line -> Log.d(TAG, line));
            Thread stderrThread = drainStream(process.getErrorStream(), line -> Log.d(TAG, line));

            appendLog("✓ 自动化服务已启动，等待执行结果...\n");

            automationRunning = true;
            setOperationInProgress(false);

            backgroundExecutor.execute(() -> {
                try {
                    int exitCode = process.waitFor();
                    try {
                        stdoutThread.join();
                        stderrThread.join();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    automationRunning = false;
                    appendLog(String.format(Locale.CHINA,
                            "\nℹ️ 自动化任务已结束 (代码: %d)\n", exitCode));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    appendLog("⚠️ 等待自动化任务结束被中断\n");
                    automationRunning = false;
                } finally {
                    updateActionButtons();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "启动自动化任务失败", e);
            appendLog("✗ 启动失败，请检查设备\n");
            automationRunning = false;
            setOperationInProgress(false);
            updateActionButtons();
        }
    }

    private void stopAutomation() {
        try {
            boolean stopped = killInstrumentationProcesses();
            if (stopped) {
                automationRunning = false;
                appendLog("✓ 自动化服务已停止\n");
            } else {
                appendLog("✗ 未找到需要停止的进程\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "停止自动化任务失败", e);
            appendLog("✗ 停止失败，请检查设备\n");
        } finally {
            automationRunning = false;
            setOperationInProgress(false);
            updateActionButtons();
        }
    }

    private void persistCredentials(String url, String username, String password) {
        preferences.edit()
                .putString(KEY_SERVER_URL, url)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .apply();
    }

    private void saveInstalledChecksums(String mainChecksum, String testChecksum) {
        SharedPreferences.Editor editor = preferences.edit();
        if (!TextUtils.isEmpty(mainChecksum)) {
            editor.putString(KEY_MAIN_APK_CHECKSUM, mainChecksum);
        } else {
            editor.remove(KEY_MAIN_APK_CHECKSUM);
        }
        if (!TextUtils.isEmpty(testChecksum)) {
            editor.putString(KEY_TEST_APK_CHECKSUM, testChecksum);
        } else {
            editor.remove(KEY_TEST_APK_CHECKSUM);
        }
        editor.apply();
    }

    private void setOperationInProgress(boolean inProgress) {
        operationInProgress = inProgress;
        mainHandler.post(() -> {
            binding.progressIndicator.setVisibility(inProgress ? View.VISIBLE : View.GONE);
            updateActionButtons();
        });
    }

    private void appendLog(String text) {
        mainHandler.post(() -> {
            CharSequence current = binding.textLog.getText();
            if (current != null && current.toString().contentEquals(getString(R.string.label_log_placeholder))) {
                binding.textLog.setText("");
            }
            binding.textLog.append(text);
            binding.logScrollView.post(() -> binding.logScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void runAsync(Runnable task) {
        backgroundExecutor.execute(task);
    }

    private void clearLog() {
        mainHandler.post(() -> binding.textLog.setText(""));
    }

    private ShellResult runShell(String rawCommand, boolean asRoot, boolean streamOutput)
            throws IOException, InterruptedException {

        String[] command = asRoot
                ? new String[] { "su", "-c", rawCommand }
                : new String[] { "sh", "-c", rawCommand };

        Process process = Runtime.getRuntime().exec(command);
        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();

        Thread stdoutThread = drainStream(process.getInputStream(), line -> {
            if (streamOutput) {
                appendLog(line + "\n");
            } else {
                stdoutBuilder.append(line).append('\n');
            }
        });

        Thread stderrThread = drainStream(process.getErrorStream(), line -> {
            if (streamOutput) {
                appendLog(line + "\n");
            } else {
                stderrBuilder.append(line).append('\n');
            }
        });

        int exitCode = process.waitFor();
        stdoutThread.join();
        stderrThread.join();

        return new ShellResult(exitCode, stdoutBuilder.toString(), stderrBuilder.toString());
    }

    private Thread drainStream(InputStream stream, LineHandler handler) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handler.onLine(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "读取进程输出失败", e);
            }
        });
        thread.start();
        return thread;
    }

    private void updateActionButtons() {
        mainHandler.post(() -> {
            binding.buttonLaunch.setEnabled(!operationInProgress && !automationRunning);
            binding.buttonStop.setEnabled(!operationInProgress && automationRunning);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundExecutor.shutdownNow();
    }

    private interface LineHandler {
        void onLine(String line);
    }

    private static final class ShellResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ShellResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        boolean isSuccess() {
            return exitCode == 0;
        }
    }

    private static String requireText(CharSequence sequence) {
        return sequence == null ? "" : sequence.toString().trim();
    }

    private boolean forceStopPackage(String packageName) throws IOException, InterruptedException {
        ShellResult result = runShell("am force-stop " + packageName, true, false);
        if (!result.isSuccess()) {
            appendLog("⚠️ 停止进程的高权限尝试失败，继续使用备用方案\n");
            result = runShell("am force-stop " + packageName, false, false);
        }
        if (result.isSuccess()) {
            appendLog("✓ 相关进程已停止\n");
        }
        return result.isSuccess();
    }

    private boolean killInstrumentationProcesses() throws IOException, InterruptedException {
        boolean stopped = false;
        stopped |= forceStopPackage(activeTestPackage);
        stopped |= forceStopPackage(activeTargetPackage);
        stopped |= killProcessByName(activeTestPackage);
        stopped |= killProcessByName(activeTargetPackage);
        return stopped;
    }

    private boolean killProcessByName(String processName) throws IOException, InterruptedException {
        ShellResult pidResult = runShell("pidof " + processName, true, false);
        String stdout = pidResult.stdout != null ? pidResult.stdout.trim() : "";
        if (!pidResult.isSuccess() || TextUtils.isEmpty(stdout)) {
            return false;
        }
        String[] pids = stdout.split("\\s+");
        boolean killedAny = false;
        for (String pid : pids) {
            ShellResult killResult = runShell("kill -9 " + pid, true, false);
            if (killResult.isSuccess()) {
                killedAny = true;
            }
        }
        if (killedAny) {
            appendLog("✓ 多余进程已清理\n");
        }
        return killedAny;
    }
}
