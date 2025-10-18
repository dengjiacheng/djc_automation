package com.automation.application.runtime.modules;

import androidx.test.uiautomator.UiDevice;

import com.automation.domain.command.CommandContext;
import com.automation.domain.command.CommandModule;
import com.automation.domain.command.CommandParameter;
import com.automation.domain.command.CommandRegistry;
import com.automation.domain.command.CommandResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

/**
 * 设备层的交互指令（点击、滑动、按键）。
 */
public final class DeviceInteractionModule implements CommandModule {

    private static final List<CommandParameter> CLICK_PARAMS = Arrays.asList(
            CommandParameter.required("x", "int", "X 坐标", 0),
            CommandParameter.required("y", "int", "Y 坐标", 0)
    );

    private static final List<CommandParameter> SWIPE_PARAMS = Arrays.asList(
            CommandParameter.required("startX", "int", "起始 X 坐标", 0),
            CommandParameter.required("startY", "int", "起始 Y 坐标", 0),
            CommandParameter.required("endX", "int", "结束 X 坐标", 0),
            CommandParameter.required("endY", "int", "结束 Y 坐标", 0),
            CommandParameter.optional("steps", "int", "滑动步数，默认50", 50)
    );

    private static final List<CommandParameter> KEY_PARAMS = List.of(
            CommandParameter.required("keycode", "int", "Android KeyEvent 键值", 3)
    );

    private final UiDevice uiDevice;

    public DeviceInteractionModule(UiDevice uiDevice) {
        this.uiDevice = uiDevice;
    }

    @Override
    public void register(CommandRegistry registry) {
        registry.register("click", "点击指定坐标", CLICK_PARAMS, this::click);
        registry.register("swipe", "滑动屏幕", SWIPE_PARAMS, this::swipe);
        registry.register("press_key", "按下指定按键", KEY_PARAMS, this::pressKey);
        registry.register("press_back", "按下返回键", List.of(), this::pressBack);
        registry.register("press_home", "按下Home键", List.of(), this::pressHome);
        registry.register("screen_rotation", "屏幕旋转", List.of(),
                (ctx, params) -> CommandResult.successMessage("当前版本未实现屏幕旋转"));
    }

    private CommandResult click(CommandContext ctx, JSONObject params) throws JSONException {
        int x = params.getInt("x");
        int y = params.getInt("y");
        if (uiDevice.click(x, y)) {
            return CommandResult.successMessage("已点击坐标 (" + x + ", " + y + ")");
        }
        throw new IllegalStateException("点击失败");
    }

    private CommandResult swipe(CommandContext ctx, JSONObject params) throws JSONException {
        int startX = params.getInt("startX");
        int startY = params.getInt("startY");
        int endX = params.getInt("endX");
        int endY = params.getInt("endY");
        int steps = params.optInt("steps", 50);
        if (uiDevice.swipe(startX, startY, endX, endY, steps)) {
            return CommandResult.successMessage(
                    String.format("已滑动 (%d,%d) -> (%d,%d)", startX, startY, endX, endY));
        }
        throw new IllegalStateException("滑动失败");
    }

    private CommandResult pressKey(CommandContext ctx, JSONObject params) throws JSONException {
        int keyCode = params.getInt("keycode");
        if (uiDevice.pressKeyCode(keyCode)) {
            return CommandResult.successMessage("已按下按键: " + keyCode);
        }
        throw new IllegalStateException("按键失败: " + keyCode);
    }

    private CommandResult pressBack(CommandContext ctx, JSONObject params) {
        uiDevice.pressBack();
        return CommandResult.successMessage("已按下返回键");
    }

    private CommandResult pressHome(CommandContext ctx, JSONObject params) {
        uiDevice.pressHome();
        return CommandResult.successMessage("已按下Home键");
    }
}
