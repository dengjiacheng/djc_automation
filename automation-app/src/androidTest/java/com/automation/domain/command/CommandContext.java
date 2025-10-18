package com.automation.domain.command;

import org.json.JSONObject;

/**
 * 提供指令执行过程需要的上下文信息及实时上报能力。
 */
public interface CommandContext {

    String commandId();

    String action();

    JSONObject params();

    String userId();

    String deviceId();

    void reportProgress(String stage, String message, Integer percent, JSONObject extra);

    void reportLog(String level, String message, JSONObject extra);
}
