package com.automation.domain.scenario;

import org.json.JSONObject;

import com.automation.domain.command.CommandContext;

/**
 * 将场景事件转换为指令进度与日志上报。
 */
public final class CommandScenarioReporter implements ScenarioReporter {

    private final CommandContext context;

    public CommandScenarioReporter(CommandContext context) {
        this.context = context;
    }

    @Override
    public void onInfo(String message) {
        context.reportProgress("scene.info", message, null, null);
    }

    @Override
    public void onWarning(String message) {
        context.reportLog("warning", message, null);
    }

    @Override
    public void onError(String message, Throwable error) {
        JSONObject extra = new JSONObject();
        try {
            extra.put("message", message);
            if (error != null) {
                extra.put("exception", error.getClass().getSimpleName());
                extra.put("error_message", error.getMessage());
            }
        } catch (Exception ignored) {
            // ignore json error
        }
        context.reportLog("error", message, extra);
    }

    @Override
    public void onSceneMatched(String sceneId, String description) {
        JSONObject extra = new JSONObject();
        try {
            extra.put("scene_id", sceneId);
            extra.put("description", description);
        } catch (Exception ignored) {
        }
        context.reportProgress("scene.matched", "匹配场景: " + sceneId, null, extra);
    }

    @Override
    public void onSceneConflict(String[] sceneIds) {
        JSONObject extra = new JSONObject();
        try {
            extra.put("conflicts", sceneIds);
        } catch (Exception ignored) {
        }
        context.reportLog("error", "场景匹配冲突", extra);
    }

    @Override
    public void onTimeout() {
        context.reportLog("error", "场景匹配超时", null);
    }
}
