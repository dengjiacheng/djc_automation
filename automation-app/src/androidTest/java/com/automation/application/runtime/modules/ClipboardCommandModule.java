package com.automation.application.runtime.modules;

import com.automation.infrastructure.system.ClipboardHelper;
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
 * 剪贴板读写指令。
 */
public final class ClipboardCommandModule implements CommandModule {

    private static final List<CommandParameter> SET_PARAMS = Arrays.asList(
            CommandParameter.required("text", "string", "要写入剪贴板的文本"),
            CommandParameter.optional("label", "string", "剪贴板条目的标签", "automation")
    );

    private final ClipboardHelper clipboardHelper;

    public ClipboardCommandModule(ClipboardHelper clipboardHelper) {
        this.clipboardHelper = clipboardHelper;
    }

    @Override
    public void register(CommandRegistry registry) {
        registry.register("set_clipboard", "设置剪贴板", SET_PARAMS, this::setClipboard);
        registry.register("get_clipboard", "获取剪贴板", List.of(), this::getClipboard);
    }

    private CommandResult setClipboard(CommandContext ctx, JSONObject params) throws JSONException {
        String text = params.getString("text");
        String label = params.optString("label", "automation");
        clipboardHelper.setClipboard(label, text);
        return CommandResult.successMessage("已设置剪贴板: " + text);
    }

    private CommandResult getClipboard(CommandContext ctx, JSONObject params) throws Exception {
        String text = clipboardHelper.getClipboard();
        if (text != null) {
            JSONObject result = new JSONObject();
            result.put("text", text);
            return CommandResult.success(result);
        }
        throw new IllegalStateException("剪贴板为空");
    }
}
