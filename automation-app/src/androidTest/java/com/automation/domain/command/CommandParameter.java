package com.automation.domain.command;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 描述指令参数的结构化元数据。
 */
public final class CommandParameter {

    private final String name;
    private final String type;
    private final boolean required;
    private final String description;
    private final Object defaultValue;
    private final ParameterConstraints constraints;

    private CommandParameter(
            String name,
            String type,
            boolean required,
            String description,
            Object defaultValue,
            ParameterConstraints constraints
    ) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
        this.defaultValue = defaultValue;
        this.constraints = constraints != null ? constraints : ParameterConstraints.none();
    }

    public static CommandParameter required(String name, String type, String description) {
        return required(name, type, description, null, ParameterConstraints.none());
    }

    public static CommandParameter required(String name, String type, String description, Object defaultValue) {
        return required(name, type, description, defaultValue, ParameterConstraints.none());
    }

    public static CommandParameter required(
            String name,
            String type,
            String description,
            Object defaultValue,
            ParameterConstraints constraints
    ) {
        return new CommandParameter(name, type, true, description, defaultValue, constraints);
    }

    public static CommandParameter optional(String name, String type, String description) {
        return optional(name, type, description, null, ParameterConstraints.none());
    }

    public static CommandParameter optional(String name, String type, String description, Object defaultValue) {
        return optional(name, type, description, defaultValue, ParameterConstraints.none());
    }

    public static CommandParameter optional(
            String name,
            String type,
            String description,
            Object defaultValue,
            ParameterConstraints constraints
    ) {
        return new CommandParameter(name, type, false, description, defaultValue, constraints);
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("name", name);
            object.put("type", type);
            object.put("required", required);
            if (description != null) {
                object.put("description", description);
            }
            if (defaultValue != null) {
                object.put("default", defaultValue);
            }
        } catch (JSONException ignored) {
        }
        return object;
    }

    public String name() {
        return name;
    }

    public String rawType() {
        return type;
    }

    public ParameterType parameterType() {
        return ParameterType.fromRaw(type);
    }

    public boolean required() {
        return required;
    }

    public String description() {
        return description;
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public ParameterConstraints constraints() {
        return constraints;
    }
}
