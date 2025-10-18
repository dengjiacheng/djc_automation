package com.automation.domain.command;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 指令注册门面，负责把模块声明委托给 {@link CommandBus}。
 */
public final class CommandRegistry {

    private final CommandBus commandBus;

    public CommandRegistry(CommandBus commandBus) {
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus");
    }

    public void register(String action,
                         String description,
                         List<CommandParameter> parameters,
                         CommandHandler handler) {
        register(action, description, parameters, handler, null);
    }

    public void register(String action,
                         String description,
                         List<CommandParameter> parameters,
                         CommandHandler handler,
                         Supplier<JSONObject> metadataSupplier) {
        CommandDescriptor descriptor = CommandDescriptor.builder(action)
                .description(description)
                .parameters(parameters)
                .metadataSupplier(metadataSupplier)
                .build();
        commandBus.register(descriptor, handler);
    }

    public void register(CommandDescriptor descriptor, CommandHandler handler) {
        commandBus.register(descriptor, handler);
    }

    public void registerDescriptor(String action,
                                   String description,
                                   List<CommandParameter> parameters,
                                   Supplier<JSONObject> metadataSupplier) {
        CommandDescriptor descriptor = CommandDescriptor.builder(action)
                .description(description)
                .parameters(parameters)
                .metadataSupplier(metadataSupplier)
                .build();
        commandBus.registerDescriptor(descriptor);
    }

    public void registerDescriptor(CommandDescriptor descriptor) {
        commandBus.registerDescriptor(descriptor);
    }

    public void addInterceptor(CommandInterceptor interceptor) {
        commandBus.addInterceptor(interceptor);
    }

    public JSONArray capabilitiesAsJson() {
        return commandBus.capabilitiesAsJson();
    }
}
