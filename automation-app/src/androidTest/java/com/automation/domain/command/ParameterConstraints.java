package com.automation.domain.command;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Optional constraints for command parameters.
 */
public final class ParameterConstraints {

    private static final ParameterConstraints NONE = new Builder().build();

    private final boolean allowBlank;
    private final Set<String> enumValues;

    private ParameterConstraints(boolean allowBlank, Set<String> enumValues) {
        this.allowBlank = allowBlank;
        this.enumValues = enumValues;
    }

    public boolean allowBlank() {
        return allowBlank;
    }

    public boolean hasEnumValues() {
        return !enumValues.isEmpty();
    }

    public Set<String> enumValues() {
        return enumValues;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ParameterConstraints none() {
        return NONE;
    }

    public static final class Builder {
        private boolean allowBlank;
        private final Set<String> enumValues = new LinkedHashSet<>();

        public Builder allowBlank(boolean value) {
            this.allowBlank = value;
            return this;
        }

        public Builder enumValues(Collection<String> values) {
            if (values != null) {
                enumValues.addAll(values);
            }
            return this;
        }

        public ParameterConstraints build() {
            return new ParameterConstraints(
                    allowBlank,
                    Collections.unmodifiableSet(new LinkedHashSet<>(enumValues))
            );
        }
    }
}
