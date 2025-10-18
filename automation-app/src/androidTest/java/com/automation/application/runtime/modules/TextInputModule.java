package com.automation.application.runtime.modules;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import com.automation.domain.command.CommandContext;
import com.automation.domain.command.CommandModule;
import com.automation.domain.command.CommandParameter;
import com.automation.domain.command.CommandRegistry;
import com.automation.domain.command.CommandResult;
import com.automation.shared.util.EncodingUtils;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 文本输入相关指令，支持直接设置与通过 ADB Keyboard 输入。
 */
public final class TextInputModule implements CommandModule {

    private static final String TAG = "TextInputModule";
    private static final String METHOD_DIRECT = "direct";
    private static final String METHOD_IME = "ime";
    private static final String ADB_KEYBOARD_PACKAGE = "com.automation";
    private static final String ADB_KEYBOARD_COMPONENT = "com.automation/.AdbKeyboard";

    private static final List<CommandParameter> INPUT_PARAMS = Arrays.asList(
            CommandParameter.required("text", "string", "要输入的文本"),
            CommandParameter.optional("method", "string", "输入方式: direct 或 ime", METHOD_DIRECT)
    );

    private static final List<CommandParameter> CLEAR_PARAMS = List.of(
            CommandParameter.optional("method", "string", "清空方式: direct 或 ime", METHOD_DIRECT)
    );

    private final Context appContext;
    private final UiDevice uiDevice;

    public TextInputModule(Context appContext, UiDevice uiDevice) {
        this.appContext = appContext.getApplicationContext();
        this.uiDevice = uiDevice;
    }

    @Override
    public void register(CommandRegistry registry) {
        registry.register("input_text", "向当前焦点控件输入文本", INPUT_PARAMS, this::inputText);
        registry.register("clear_text", "清空当前焦点控件文本", CLEAR_PARAMS, this::clearText);
    }

    private CommandResult inputText(CommandContext ctx, JSONObject params) throws Exception {
        String text = params.getString("text");
        String method = params.optString("method", METHOD_DIRECT).toLowerCase(Locale.ROOT);
        switch (method) {
            case METHOD_IME -> inputViaIme(text, true);
            case METHOD_DIRECT -> inputDirect(text);
            default -> throw new IllegalArgumentException("不支持的输入方式: " + method);
        }
        return CommandResult.successMessage("文本输入完成 (" + method + ")");
    }

    private CommandResult clearText(CommandContext ctx, JSONObject params) throws Exception {
        String method = params.optString("method", METHOD_DIRECT).toLowerCase(Locale.ROOT);
        switch (method) {
            case METHOD_IME -> inputViaIme(null, false);
            case METHOD_DIRECT -> clearDirect();
            default -> throw new IllegalArgumentException("不支持的清空方式: " + method);
        }
        return CommandResult.successMessage("文本已清空 (" + method + ")");
    }

    private void inputDirect(String text) {
        UiObject2 focused = findFocusedEditable();
        focused.setText(text);
    }

    private void clearDirect() {
        UiObject2 focused = findFocusedEditable();
        focused.clear();
    }

    private UiObject2 findFocusedEditable() {
        UiObject2 focused = uiDevice.findObject(By.focused(true));
        if (focused == null) {
            throw new IllegalStateException("未找到获取焦点的输入控件");
        }
        return focused;
    }

    private void inputViaIme(String text, boolean setText) {
        if (!isAdbKeyboardAvailable()) {
            throw new IllegalStateException("设备未安装或未启用 ADB Keyboard IME (com.automation)");
        }
        ensureImeEnabled();
        String action = setText
                ? "ADB_KEYBOARD_SET_TEXT"
                : "ADB_KEYBOARD_CLEAR_TEXT";
        Intent intent = new Intent(action);
        intent.setPackage(ADB_KEYBOARD_PACKAGE);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        if (text != null) {
            String encoded = EncodingUtils.encodeBase64(text.getBytes(StandardCharsets.UTF_8));
            intent.putExtra("text", encoded);
        }
        appContext.sendBroadcast(intent);
    }

    private boolean isAdbKeyboardAvailable() {
        PackageManager pm = appContext.getPackageManager();
        if (pm == null) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(ADB_KEYBOARD_PACKAGE, PackageManager.PackageInfoFlags.of(0));
            } else {
                //noinspection deprecation
                pm.getPackageInfo(ADB_KEYBOARD_PACKAGE, 0);
            }
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void ensureImeEnabled() {
        try {
            uiDevice.executeShellCommand("ime enable " + ADB_KEYBOARD_COMPONENT);
            uiDevice.executeShellCommand("ime set " + ADB_KEYBOARD_COMPONENT);
        } catch (Exception ex) {
            Log.w(TAG, "无法通过 shell 命令自动启用输入法", ex);
        }
    }
}
