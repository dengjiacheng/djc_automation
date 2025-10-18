package com.automation.domain.scenario.script;

import java.util.Objects;

/**
 * 描述脚本运行时支持的参数。
 */
public final class ScriptParameterSpec {

    private final String name;
    private final String type;
    private final String description;
    private final boolean required;
    private final Object defaultValue;

    public ScriptParameterSpec(String name,
                               String type,
                               String description,
                               boolean required,
                               Object defaultValue) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = type;
        this.description = description;
        this.required = required;
        this.defaultValue = defaultValue;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public String description() {
        return description;
    }

    public boolean required() {
        return required;
    }

    public Object defaultValue() {
        return defaultValue;
    }
}
