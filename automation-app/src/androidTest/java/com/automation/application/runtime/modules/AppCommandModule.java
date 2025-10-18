package com.automation.application.runtime.modules;

import com.automation.infrastructure.system.AppManager;
import com.automation.domain.command.CommandContext;
import com.automation.domain.command.CommandModule;
import com.automation.domain.command.CommandParameter;
import com.automation.domain.command.CommandRegistry;
import com.automation.domain.command.CommandResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * 应用管理相关指令。
 */
public final class AppCommandModule implements CommandModule {

    private static final List<CommandParameter> PACKAGE_PARAM =
            List.of(CommandParameter.required("package", "string", "应用包名"));

    private static final List<CommandParameter> LIST_APPS_PARAMS =
            List.of(CommandParameter.optional("third_party_only", "bool", "仅返回第三方应用", false));

    private final AppManager appManager;

    public AppCommandModule(AppManager appManager) {
        this.appManager = appManager;
    }

    @Override
    public void register(CommandRegistry registry) {
        registry.register("launch_app", "启动应用", PACKAGE_PARAM, this::launchApp);
        registry.register("stop_app", "停止应用", PACKAGE_PARAM, this::stopApp);
        registry.register("clear_app", "清除应用数据", PACKAGE_PARAM, this::clearAppData);
        registry.register("list_apps", "获取已安装应用列表", LIST_APPS_PARAMS, this::listInstalledApps);
    }

    private CommandResult launchApp(CommandContext ctx, JSONObject params) throws JSONException {
        String packageName = params.getString("package");
        if (appManager.launchApp(packageName)) {
            return CommandResult.successMessage("已启动应用: " + packageName);
        }
        throw new IllegalStateException("启动应用失败: " + packageName);
    }

    private CommandResult stopApp(CommandContext ctx, JSONObject params) throws JSONException {
        String packageName = params.getString("package");
        if (appManager.stopApp(packageName)) {
            return CommandResult.successMessage("已停止应用: " + packageName);
        }
        throw new IllegalStateException("停止应用失败: " + packageName);
    }

    private CommandResult clearAppData(CommandContext ctx, JSONObject params) throws JSONException {
        String packageName = params.getString("package");
        if (appManager.clearAppData(packageName)) {
            return CommandResult.successMessage("已清除应用数据: " + packageName);
        }
        throw new IllegalStateException("清除应用数据失败: " + packageName);
    }

    private CommandResult listInstalledApps(CommandContext ctx, JSONObject params) throws JSONException {
        boolean thirdPartyOnly = params.optBoolean("third_party_only", false);
        List<AppManager.InstalledAppInfo> apps = appManager.listInstalledApps(thirdPartyOnly);
        JSONArray appArray = new JSONArray();
        for (AppManager.InstalledAppInfo app : apps) {
            JSONObject appJson = new JSONObject();
            appJson.put("package", app.packageName);
            appJson.put("name", app.label);
            appJson.put("version_name", app.versionName);
            appJson.put("version_code", app.versionCode);
            appJson.put("system_app", app.systemApp);
            appArray.put(appJson);
        }
        JSONObject result = new JSONObject();
        result.put("count", apps.size());
        result.put("third_party_only", thirdPartyOnly);
        result.put("apps", appArray);
        return CommandResult.success(result);
    }
}
