package com.automation.infrastructure.system;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * APP 管理增强
 * 基于 UIAutomator executeShellCommand() 提供应用管理功能
 */
public class AppManager {
    private static final int LAUNCH_TIMEOUT = 5000;
    private static final String TAG = "AppManager";

    private Context context;
    private UiDevice device;

    public AppManager(Context context, UiDevice device) {
        this.context = context;
        this.device = device;
    }

    /**
     * 启动应用
     * @param packageName 包名
     * @return 是否启动成功
     */
    public boolean launchApp(String packageName) {
        android.util.Log.i(TAG, "Launch " + packageName);
        try {
            final Intent intent = context.getPackageManager()
                    .getLaunchIntentForPackage(packageName);
            if (intent == null) {
                android.util.Log.e(TAG, "Package not found: " + packageName);
                return false;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);

            // 等待应用启动
            return device.wait(Until.hasObject(By.pkg(packageName).depth(0)), LAUNCH_TIMEOUT);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Launch failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 停止应用（使用 am force-stop）
     * 需要设备已 root 或通过 adb shell 运行
     */
    public boolean stopApp(String packageName) {
        try {
            String result = device.executeShellCommand("am force-stop " + packageName);
            android.util.Log.i(TAG, "Stop app: " + packageName + ", result: " + result);
            return true;
        } catch (IOException e) {
            android.util.Log.e(TAG, "Stop failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 清除应用数据（使用 pm clear）
     * 需要设备已 root 或通过 adb shell 运行
     */
    public boolean clearAppData(String packageName) {
        try {
            String result = device.executeShellCommand("pm clear " + packageName);
            android.util.Log.i(TAG, "Clear app data: " + packageName + ", result: " + result);
            // pm clear 成功会返回 "Success"
            return result != null && result.contains("Success");
        } catch (IOException e) {
            android.util.Log.e(TAG, "Clear failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取当前前台应用包名
     * UIAutomator 原生方法
     */
    public String getCurrentPackageName() {
        return device.getCurrentPackageName();
    }

    /**
     * 获取启动器包名
     * UIAutomator 原生方法
     */
    public String getLauncherPackageName() {
        return device.getLauncherPackageName();
    }

    /**
     * 判断应用是否已安装
     */
    public boolean isAppInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * 等待应用启动
     */
    public boolean waitForApp(String packageName, long timeout) {
        return device.wait(Until.hasObject(By.pkg(packageName).depth(0)), timeout);
    }

    /**
     * 判断应用是否在前台运行
     */
    public boolean isAppInForeground(String packageName) {
        return packageName.equals(getCurrentPackageName());
    }

    /**
     * 执行 Shell 命令
     * UIAutomator 原生方法 - device.executeShellCommand()
     */
    public String executeShellCommand(String command) throws IOException {
        return device.executeShellCommand(command);
    }

    /**
     * 启动 Activity
     */
    public boolean startActivity(String packageName, String activityName) {
        try {
            String cmd = String.format("am start -n %s/%s", packageName, activityName);
            String result = device.executeShellCommand(cmd);
            android.util.Log.i(TAG, "Start activity: " + cmd + ", result: " + result);
            return result != null && !result.contains("Error");
        } catch (IOException e) {
            android.util.Log.e(TAG, "Start activity failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 重启应用（先停止再启动）
     */
    public boolean restartApp(String packageName) {
        stopApp(packageName);
        try {
            Thread.sleep(500); // 等待应用完全停止
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return launchApp(packageName);
    }

    /**
     * 授予运行时权限
     * 例如: grantPermission("com.example.app", "android.permission.CAMERA")
     */
    public boolean grantPermission(String packageName, String permission) {
        try {
            String cmd = String.format("pm grant %s %s", packageName, permission);
            String result = device.executeShellCommand(cmd);
            android.util.Log.i(TAG, "Grant permission: " + cmd + ", result: " + result);
            return true;
        } catch (IOException e) {
            android.util.Log.e(TAG, "Grant permission failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 撤销运行时权限
     */
    public boolean revokePermission(String packageName, String permission) {
        try {
            String cmd = String.format("pm revoke %s %s", packageName, permission);
            String result = device.executeShellCommand(cmd);
            android.util.Log.i(TAG, "Revoke permission: " + cmd + ", result: " + result);
            return true;
        } catch (IOException e) {
            android.util.Log.e(TAG, "Revoke permission failed: " + e.getMessage());
            return false;
        }
    }

    public List<InstalledAppInfo> listInstalledApps(boolean thirdPartyOnly) {
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(0);
        List<InstalledAppInfo> result = new ArrayList<>(packages.size());
        for (PackageInfo pkg : packages) {
            ApplicationInfo appInfo = pkg.applicationInfo;
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (thirdPartyOnly && isSystemApp) {
                continue;
            }
            CharSequence label = pm.getApplicationLabel(appInfo);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? pkg.getLongVersionCode()
                    : pkg.versionCode;
            result.add(new InstalledAppInfo(
                    appInfo.packageName,
                    label != null ? label.toString() : appInfo.packageName,
                    pkg.versionName,
                    versionCode,
                    isSystemApp
            ));
        }
        return result;
    }

    public static final class InstalledAppInfo {
        public final String packageName;
        public final String label;
        public final String versionName;
        public final long versionCode;
        public final boolean systemApp;

        public InstalledAppInfo(String packageName, String label, String versionName, long versionCode, boolean systemApp) {
            this.packageName = packageName;
            this.label = label;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.systemApp = systemApp;
        }
    }
}
