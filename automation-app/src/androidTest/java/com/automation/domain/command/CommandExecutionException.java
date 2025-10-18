package com.automation.domain.command;

/**
 * 指令执行异常。
 */
public final class CommandExecutionException extends Exception {

    public CommandExecutionException(String message) {
        super(message);
    }

    public CommandExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
