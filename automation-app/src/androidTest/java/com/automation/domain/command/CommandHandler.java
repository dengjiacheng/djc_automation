package com.automation.domain.command;

import org.json.JSONObject;

/**
 * 指令处理器接口。
 */
@FunctionalInterface
public interface CommandHandler {

    CommandResult handle(CommandContext context, JSONObject params) throws Exception;
}
