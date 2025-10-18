package com.automation.application.runtime.interceptors;

import android.os.SystemClock;
import android.util.Log;

import com.automation.domain.command.CommandContext;
import com.automation.domain.command.CommandInterceptor;
import com.automation.domain.command.CommandInvocation;
import com.automation.domain.command.CommandResult;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 统一的日志与耗时拦截器。
 */
public final class CommandLoggingInterceptor implements CommandInterceptor {

    private static final String TAG = "CommandLogger";

    @Override
    public CommandResult intercept(CommandInvocation invocation) throws Exception {
        String action = invocation.descriptor().action();
        long start = SystemClock.elapsedRealtime();
        Log.i(TAG, "→ 执行指令: " + action);
        try {
            CommandResult result = invocation.proceed();
            long elapsed = SystemClock.elapsedRealtime() - start;
            logToContext(invocation.context(), "info", buildMessage(action, result, elapsed), elapsed, result);
            Log.i(TAG, String.format("✓ 指令完成: %s (%.1f ms)", action, (double) elapsed));
            return result;
        } catch (Exception ex) {
            long elapsed = SystemClock.elapsedRealtime() - start;
            logToContext(invocation.context(), "error",
                    "指令执行异常: " + action + " (" + ex.getMessage() + ")",
                    elapsed, null);
            Log.e(TAG, String.format("✗ 指令失败: %s (%.1f ms)", action, (double) elapsed), ex);
            throw ex;
        }
    }

    private void logToContext(CommandContext context,
                              String level,
                              String message,
                              long elapsedMs,
                              CommandResult result) {
        if (context == null) {
            return;
        }
        try {
            JSONObject extra = new JSONObject();
            extra.put("duration_ms", elapsedMs);
            if (result != null && result.message() != null) {
                extra.put("result_message", result.message());
            }
            context.reportLog(level, message, extra);
        } catch (JSONException ignore) {
            context.reportLog(level, message, null);
        }
    }

    private String buildMessage(String action, CommandResult result, long elapsedMs) {
        String status = (result == null || result.isSuccess()) ? "success" : "failure";
        StringBuilder builder = new StringBuilder();
        builder.append("指令 ").append(action).append(" 完成 (").append(status).append(")");
        builder.append("，耗时 ").append(elapsedMs).append(" ms");
        if (result != null && result.message() != null) {
            builder.append("，备注: ").append(result.message());
        }
        return builder.toString();
    }
}
