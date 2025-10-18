package com.automation.application.runtime.modules;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.test.uiautomator.UiDevice;

import com.automation.infrastructure.system.ScreenshotHelper;
import com.automation.domain.command.CommandContext;
import com.automation.domain.command.CommandModule;
import com.automation.domain.command.CommandParameter;
import com.automation.domain.command.CommandRegistry;
import com.automation.domain.command.CommandResult;
import com.automation.shared.util.CompressionUtils;
import com.automation.shared.util.EncodingUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 诊断类指令：截图、导出层级、查询电量等。
 */
public final class DiagnosticsCommandModule implements CommandModule {

    private static final List<CommandParameter> SCREENSHOT_PARAMS =
            List.of(CommandParameter.optional("quality", "int", "JPEG质量(0-100)", 80));

    private static final List<CommandParameter> HIERARCHY_PARAMS =
            List.of(CommandParameter.optional("compress", "bool", "是否压缩", true));

    private final Context context;
    private final UiDevice uiDevice;
    private final ScreenshotHelper screenshotHelper;

    public DiagnosticsCommandModule(Context context, UiDevice uiDevice, ScreenshotHelper screenshotHelper) {
        this.context = context.getApplicationContext();
        this.uiDevice = uiDevice;
        this.screenshotHelper = screenshotHelper;
    }

    @Override
    public void register(CommandRegistry registry) {
        registry.register("screenshot", "截取屏幕", SCREENSHOT_PARAMS, this::screenshot);
        registry.register("dump_hierarchy", "导出当前界面层级", HIERARCHY_PARAMS, this::dumpHierarchy);
        registry.register("get_battery", "查询电量状态", List.of(), this::getBatteryInfo);
    }

    private CommandResult screenshot(CommandContext context, JSONObject params) throws Exception {
        int quality = params.optInt("quality", 80);
        byte[] jpegData = screenshotHelper.captureToJpegBytes(quality);
        if (jpegData == null) {
            throw new IllegalStateException("截图失败");
        }
        int originalSize = jpegData.length;
        byte[] gzipData = CompressionUtils.gzip(jpegData);
        String base64 = EncodingUtils.encodeBase64(gzipData);
        JSONObject result = new JSONObject();
        result.put("image", base64);
        result.put("original_size", originalSize);
        result.put("compressed_size", gzipData.length);
        result.put("quality", quality);
        result.put("format", "jpeg");
        result.put("gzipped", true);
        return CommandResult.success(result);
    }

    private CommandResult dumpHierarchy(CommandContext context, JSONObject params) throws Exception {
        context.reportProgress("dump_hierarchy", "开始获取UI层级", null, null);
        boolean compress = params.optBoolean("compress", true);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        uiDevice.dumpWindowHierarchy(outputStream);
        byte[] dumpData = outputStream.toByteArray();
        int originalSize = dumpData.length;
        if (compress) {
            dumpData = CompressionUtils.gzip(dumpData);
        }
        context.reportProgress("dump_hierarchy", "层级获取完成", 100, null);
        String base64 = EncodingUtils.encodeBase64(dumpData);
        JSONObject result = new JSONObject();
        result.put("data", base64);
        result.put("original_size", originalSize);
        result.put("compressed_size", dumpData.length);
        result.put("compressed", compress);
        return CommandResult.success(result);
    }

    private CommandResult getBatteryInfo(CommandContext commandContext, JSONObject params) throws JSONException {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        int level = -1;
        boolean isCharging = false;
        if (batteryStatus != null) {
            int rawLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (rawLevel >= 0 && scale > 0) {
                level = (int) ((rawLevel / (float) scale) * 100);
            }
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }
        JSONObject result = new JSONObject();
        result.put("level", level);
        result.put("is_charging", isCharging);
        return CommandResult.success(result);
    }
}
