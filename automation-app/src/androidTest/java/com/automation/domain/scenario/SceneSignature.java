package com.automation.domain.scenario;

import com.automation.domain.scenario.accessibility.AccessibilitySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 场景识别签名，描述当前界面满足/禁止的元素条件。
 */
public final class SceneSignature {

    private final List<SelectorCondition> requiredAll;
    private final List<SelectorCondition> requiredAny;
    private final List<SelectorCondition> forbiddenAny;
    private final List<SelectorCondition> forbiddenAll;

    private SceneSignature(Builder builder) {
        this.requiredAll = Collections.unmodifiableList(new ArrayList<>(builder.requiredAll));
        this.requiredAny = Collections.unmodifiableList(new ArrayList<>(builder.requiredAny));
        this.forbiddenAny = Collections.unmodifiableList(new ArrayList<>(builder.forbiddenAny));
        this.forbiddenAll = Collections.unmodifiableList(new ArrayList<>(builder.forbiddenAll));
    }

    public boolean matches(AccessibilitySnapshot snapshot, LegacyVariableResolver resolver) {
        if (snapshot == null || snapshot.isEmpty()) {
            return requiredAll.isEmpty() && requiredAny.isEmpty() && forbiddenAny.isEmpty() && forbiddenAll.isEmpty();
        }

        if (!forbiddenAny.isEmpty()) {
            for (SelectorCondition condition : forbiddenAny) {
                if (snapshot.exists(condition.resolve(resolver))) {
                    return false;
                }
            }
        }

        if (!forbiddenAll.isEmpty()) {
            boolean allPresent = true;
            for (SelectorCondition condition : forbiddenAll) {
                if (!snapshot.exists(condition.resolve(resolver))) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) {
                return false;
            }
        }

        for (SelectorCondition condition : requiredAll) {
            if (!snapshot.exists(condition.resolve(resolver))) {
                return false;
            }
        }

        if (!requiredAny.isEmpty()) {
            boolean anyPresent = false;
            for (SelectorCondition condition : requiredAny) {
                if (snapshot.exists(condition.resolve(resolver))) {
                    anyPresent = true;
                    break;
                }
            }
            if (!anyPresent) {
                return false;
            }
        }

        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<SelectorCondition> requiredAll() {
        return requiredAll;
    }

    public List<SelectorCondition> requiredAny() {
        return requiredAny;
    }

    public List<SelectorCondition> forbiddenAny() {
        return forbiddenAny;
    }

    public List<SelectorCondition> forbiddenAll() {
        return forbiddenAll;
    }

    public static final class Builder {
        // 通过链式 API 逐步累加不同条件。
        private final List<SelectorCondition> requiredAll = new ArrayList<>();
        private final List<SelectorCondition> requiredAny = new ArrayList<>();
        private final List<SelectorCondition> forbiddenAny = new ArrayList<>();
        private final List<SelectorCondition> forbiddenAll = new ArrayList<>();

        private Builder() {
        }

        public Builder requireAll(SelectorCondition condition) {
            requiredAll.add(Objects.requireNonNull(condition));
            return this;
        }

        public Builder requireAny(SelectorCondition condition) {
            requiredAny.add(Objects.requireNonNull(condition));
            return this;
        }

        public Builder forbidAny(SelectorCondition condition) {
            forbiddenAny.add(Objects.requireNonNull(condition));
            return this;
        }

        public Builder forbidAll(SelectorCondition condition) {
            forbiddenAll.add(Objects.requireNonNull(condition));
            return this;
        }

        public Builder requireAll(List<SelectorCondition> conditions) {
            requiredAll.addAll(conditions);
            return this;
        }

        public Builder requireAny(List<SelectorCondition> conditions) {
            requiredAny.addAll(conditions);
            return this;
        }

        public Builder forbidAny(List<SelectorCondition> conditions) {
            forbiddenAny.addAll(conditions);
            return this;
        }

        public Builder forbidAll(List<SelectorCondition> conditions) {
            forbiddenAll.addAll(conditions);
            return this;
        }

        public SceneSignature build() {
            return new SceneSignature(this);
        }
    }
}
