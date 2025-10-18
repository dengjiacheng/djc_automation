package com.automation.domain.command;

import org.json.JSONObject;

public interface CommandInvocation {

    CommandContext context();

    CommandDescriptor descriptor();

    JSONObject params();

    CommandResult proceed() throws Exception;
}
