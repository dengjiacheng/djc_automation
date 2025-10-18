package com.automation.domain.command;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 描述单条指令的元信息。
 */
public final class CommandDescriptor {

    private final String action;
    private final String description;
    private final List<CommandParameter> parameters;
    private final Supplier<JSONObject> metadataSupplier;

    private CommandDescriptor(Builder builder) {
        this.action = Objects.requireNonNull(builder.action, "action");
        this.description = builder.description;
        this.parameters = builder.parameters != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.parameters))
                : List.of();
        this.metadataSupplier = builder.metadataSupplier;
    }

    public static Builder builder(String action) {
        return new Builder(action);
    }

    public String action() {
        return action;
    }

    public String description() {
        return description;
    }

    public List<CommandParameter> parameters() {
        return parameters;
    }

    public Supplier<JSONObject> metadataSupplier() {
        return metadataSupplier;
    }

    public JSONObject metadata() {
        if (metadataSupplier == null) {
            return null;
        }
        return metadataSupplier.get();
    }

    public static final class Builder {
        private final String action;
        private String description;
        private List<CommandParameter> parameters = new ArrayList<>();
        private Supplier<JSONObject> metadataSupplier;

        private Builder(String action) {
            this.action = action;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parameters(List<CommandParameter> parameters) {
            this.parameters = parameters != null ? parameters : List.of();
            return this;
        }

        public Builder metadataSupplier(Supplier<JSONObject> metadataSupplier) {
            this.metadataSupplier = metadataSupplier;
            return this;
        }

        public CommandDescriptor build() {
            return new CommandDescriptor(this);
        }
    }
}
