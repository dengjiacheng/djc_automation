package com.automation.domain.command;

public interface CommandInterceptor {

    CommandResult intercept(CommandInvocation invocation) throws Exception;
}
