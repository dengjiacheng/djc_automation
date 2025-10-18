package com.automation.domain.command;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 新的指令总线，负责任务参数归一化、拦截器调度和处理器路由。
 */
public final class CommandBus {

    private static final String TAG = "CommandBus";

    private final Map<String, CommandDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, CommandHandler> handlers = new LinkedHashMap<>();
    private final List<CommandInterceptor> interceptors = new CopyOnWriteArrayList<>();

    public void register(CommandDescriptor descriptor, CommandHandler handler) {
        descriptors.put(descriptor.action(), descriptor);
        if (handler != null) {
            handlers.put(descriptor.action(), handler);
        }
    }

    public void registerDescriptor(CommandDescriptor descriptor) {
        descriptors.put(descriptor.action(), descriptor);
    }

    public void addInterceptor(CommandInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    public CommandResult dispatch(CommandContext context, String action, JSONObject rawParams) throws Exception {

        CommandDescriptor descriptor = descriptors.get(action);
        if (descriptor == null) {
            throw new IllegalArgumentException("未知指令: " + action);
        }
        CommandHandler handler = handlers.get(action);
        if (handler == null) {
            throw new IllegalStateException("指令未提供处理器: " + action);
        }

        JSONObject normalized = normalizeParameters(descriptor, rawParams);

        InvocationChain chain = new InvocationChain(context, descriptor, normalized, handler, interceptors);
        CommandResult result = chain.proceed();
        if (result == null) {
            throw new CommandExecutionException("指令未返回结果: " + action);
        }
        Log.d(TAG, "Action=" + action + " completed with success=" + result.isSuccess());
        return result;
    }

    public JSONArray capabilitiesAsJson() {
        JSONArray array = new JSONArray();
        for (CommandDescriptor descriptor : descriptors.values()) {
            JSONObject object = new JSONObject();
            try {
                object.put("action", descriptor.action());
                if (descriptor.description() != null) {
                    object.put("description", descriptor.description());
                }
                JSONArray paramsArray = new JSONArray();
                for (CommandParameter parameter : descriptor.parameters()) {
                    paramsArray.put(parameter.toJson());
                }
                object.put("params", paramsArray);
                JSONObject meta = descriptor.metadata();
                if (meta != null) {
                    object.put("meta", meta);
                }
            } catch (JSONException ignored) {
            }
            array.put(object);
        }
        return array;
    }

    private JSONObject normalizeParameters(CommandDescriptor descriptor, JSONObject params) throws JSONException {
        List<CommandParameter> definitions = descriptor.parameters();
        if (definitions == null || definitions.isEmpty()) {
            return params != null ? new JSONObject(params.toString()) : new JSONObject();
        }
        ParameterValidator validator = new ParameterValidator(definitions);
        JSONObject payload = params != null ? params : new JSONObject();
        return validator.validate(payload);
    }

    private static final class InvocationChain implements CommandInvocation {

        private final CommandContext context;
        private final CommandDescriptor descriptor;
        private final JSONObject params;
        private final CommandHandler handler;
        private final List<CommandInterceptor> interceptors;
        private int index;

        private InvocationChain(CommandContext context,
                CommandDescriptor descriptor,
                JSONObject params,
                CommandHandler handler,
                List<CommandInterceptor> interceptors) {
            this.context = context;
            this.descriptor = descriptor;
            this.params = params;
            this.handler = handler;
            this.interceptors = interceptors != null ? new ArrayList<>(interceptors) : Collections.emptyList();
        }

        @Override
        public CommandContext context() {
            return context;
        }

        @Override
        public CommandDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public JSONObject params() {
            return params;
        }

        @Override
        public CommandResult proceed() throws Exception {
            if (index < interceptors.size()) {
                CommandInterceptor interceptor = interceptors.get(index++);
                return interceptor.intercept(this);
            }
            return handler.handle(context, params);
        }
    }
}
