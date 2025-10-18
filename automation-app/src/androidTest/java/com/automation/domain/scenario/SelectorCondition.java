package com.automation.domain.scenario;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.automation.domain.scenario.accessibility.AccessibilitySnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI 选择器条件包装，支持从配置构造并带超时检测。
 * 同时提供对可访问性快照的匹配能力。
 */
public final class SelectorCondition {

    private final BySelector selector;
    private final long timeoutMs;

    private final String resourceId;
    private final Pattern resourceIdPattern;
    private final String text;
    private final String textContains;
    private final String textStartsWith;
    private final Pattern textPattern;
    private final String contentDescription;
    private final String contentDescriptionContains;
    private final String contentDescriptionStartsWith;
    private final Pattern contentDescriptionPattern;
    private final String className;
    private final Pattern classNamePattern;
    private final String packageName;
    private final Pattern packageNamePattern;
    private final Boolean clickable;
    private final Boolean enabled;
    private final Boolean selected;
    private final Boolean checkable;
    private final Boolean checked;
    private final Boolean focusable;
    private final Boolean focused;
    private final Boolean scrollable;
    private final Boolean longClickable;
    private final Map<String, Object> rawConfig;

    private SelectorCondition(BySelector selector,
                              long timeoutMs,
                              String resourceId,
                              Pattern resourceIdPattern,
                              String text,
                              String textContains,
                              String textStartsWith,
                              Pattern textPattern,
                              String contentDescription,
                              String contentDescriptionContains,
                              String contentDescriptionStartsWith,
                              Pattern contentDescriptionPattern,
                              String className,
                              Pattern classNamePattern,
                              String packageName,
                              Pattern packageNamePattern,
                              Boolean clickable,
                              Boolean enabled,
                              Boolean selected,
                              Boolean checkable,
                              Boolean checked,
                              Boolean focusable,
                              Boolean focused,
                              Boolean scrollable,
                              Boolean longClickable,
                              Map<String, Object> rawConfig) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.timeoutMs = timeoutMs;
        this.resourceId = resourceId;
        this.resourceIdPattern = resourceIdPattern;
        this.text = text;
        this.textContains = textContains;
        this.textStartsWith = textStartsWith;
        this.textPattern = textPattern;
        this.contentDescription = contentDescription;
        this.contentDescriptionContains = contentDescriptionContains;
        this.contentDescriptionStartsWith = contentDescriptionStartsWith;
        this.contentDescriptionPattern = contentDescriptionPattern;
        this.className = className;
        this.classNamePattern = classNamePattern;
        this.packageName = packageName;
        this.packageNamePattern = packageNamePattern;
        this.clickable = clickable;
        this.enabled = enabled;
        this.selected = selected;
        this.checkable = checkable;
        this.checked = checked;
        this.focusable = focusable;
        this.focused = focused;
        this.scrollable = scrollable;
        this.longClickable = longClickable;
        this.rawConfig = rawConfig != null ? rawConfig : Collections.emptyMap();
    }

    /**
     * 检查元素是否出现。
     */
    public boolean isPresent(@NonNull UiDevice device) {
        return waitFor(device, true);
    }

    /**
     * 检查元素是否消失。
     */
    public boolean isGone(@NonNull UiDevice device) {
        return waitFor(device, false);
    }

    public BySelector selector() {
        return selector;
    }

    public long timeoutMs() {
        return timeoutMs;
    }

    public boolean matchesNode(AccessibilitySnapshot.Node node) {
        if (resourceId != null && !Objects.equals(resourceId, node.resourceId())) {
            return false;
        }
        if (resourceIdPattern != null) {
            String res = node.resourceId();
            if (res == null || !resourceIdPattern.matcher(res).matches()) {
                return false;
            }
        }
        if (text != null && !Objects.equals(text, node.text())) {
            return false;
        }
        if (textContains != null) {
            String nodeText = node.text();
            if (nodeText == null || !nodeText.contains(textContains)) {
                return false;
            }
        }
        if (textStartsWith != null) {
            String nodeText = node.text();
            if (nodeText == null || !nodeText.startsWith(textStartsWith)) {
                return false;
            }
        }
        if (textPattern != null) {
            String nodeText = node.text();
            if (nodeText == null || !textPattern.matcher(nodeText).matches()) {
                return false;
            }
        }
        if (contentDescription != null && !Objects.equals(contentDescription, node.contentDescription())) {
            return false;
        }
        if (contentDescriptionContains != null) {
            String desc = node.contentDescription();
            if (desc == null || !desc.contains(contentDescriptionContains)) {
                return false;
            }
        }
        if (contentDescriptionStartsWith != null) {
            String desc = node.contentDescription();
            if (desc == null || !desc.startsWith(contentDescriptionStartsWith)) {
                return false;
            }
        }
        if (contentDescriptionPattern != null) {
            String desc = node.contentDescription();
            if (desc == null || !contentDescriptionPattern.matcher(desc).matches()) {
                return false;
            }
        }
        if (className != null && !Objects.equals(className, node.className())) {
            return false;
        }
        if (classNamePattern != null) {
            String clazz = node.className();
            if (clazz == null || !classNamePattern.matcher(clazz).matches()) {
                return false;
            }
        }
        if (packageName != null && !Objects.equals(packageName, node.packageName())) {
            return false;
        }
        if (packageNamePattern != null) {
            String pkg = node.packageName();
            if (pkg == null || !packageNamePattern.matcher(pkg).matches()) {
                return false;
            }
        }
        if (clickable != null && node.clickable() != clickable) {
            return false;
        }
        if (enabled != null && node.enabled() != enabled) {
            return false;
        }
        if (selected != null && node.selected() != selected) {
            return false;
        }
        if (checkable != null && node.checkable() != checkable) {
            return false;
        }
        if (checked != null && node.checked() != checked) {
            return false;
        }
        if (focusable != null && node.focusable() != focusable) {
            return false;
        }
        if (focused != null && node.focused() != focused) {
            return false;
        }
        if (scrollable != null && node.scrollable() != scrollable) {
            return false;
        }
        if (longClickable != null && node.longClickable() != longClickable) {
            return false;
        }
        return true;
    }

    private boolean waitFor(UiDevice device, boolean present) {
        if (timeoutMs <= 0) {
            boolean exists = device.hasObject(selector);
            return present == exists;
        }
        if (present) {
            return device.wait(Until.hasObject(selector), timeoutMs);
        } else {
            return device.wait(Until.gone(selector), timeoutMs);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 从 YAML/JSON 字段映射构造选择器。
     */
    public static SelectorCondition fromMap(Map<String, Object> config) {
        Builder builder = builder();
        Map<String, Object> raw = new LinkedHashMap<>(config);
        builder.rawConfig(raw);
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val == null) {
                continue;
            }
            switch (key) {
                case "resourceId" -> builder.resourceId(val.toString());
                case "resourceIdMatches" -> builder.resourceIdMatches(val.toString());
                case "text" -> builder.text(val.toString());
                case "textContains" -> builder.textContains(val.toString());
                case "textStartsWith" -> builder.textStartsWith(val.toString());
                case "textMatches" -> builder.textMatches(val.toString());
                case "contentDescription" -> builder.contentDescription(val.toString());
                case "contentDescriptionContains" -> builder.contentDescriptionContains(val.toString());
                case "contentDescriptionStartsWith" -> builder.contentDescriptionStartsWith(val.toString());
                case "contentDescriptionMatches" -> builder.contentDescriptionMatches(val.toString());
                case "className" -> builder.className(val.toString());
                case "classNameMatches" -> builder.classNameMatches(val.toString());
                case "packageName" -> builder.packageName(val.toString());
                case "packageNameMatches" -> builder.packageNameMatches(val.toString());
                case "clickable" -> builder.clickable(parseBoolean(val));
                case "enabled" -> builder.enabled(parseBoolean(val));
                case "selected" -> builder.selected(parseBoolean(val));
                case "checkable" -> builder.checkable(parseBoolean(val));
                case "checked" -> builder.checked(parseBoolean(val));
                case "focusable" -> builder.focusable(parseBoolean(val));
                case "focused" -> builder.focused(parseBoolean(val));
                case "scrollable" -> builder.scrollable(parseBoolean(val));
                case "longClickable" -> builder.longClickable(parseBoolean(val));
                case "timeout" -> builder.timeoutMs(parseLong(val));
                default -> {
                    // ignore unsupported keys
                }
            }
        }
        return builder.build();
    }

    private static boolean parseBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public SelectorCondition resolve(LegacyVariableResolver resolver) {
        if (rawConfig.isEmpty() || resolver == null || resolver.isEmpty()) {
            return this;
        }

        Map<String, Object> resolved = new LinkedHashMap<>(rawConfig.size());
        boolean changed = false;

        for (Map.Entry<String, Object> entry : rawConfig.entrySet()) {
            Object value = entry.getValue();
            Object newValue = value instanceof String
                    ? replacePlaceholders((String) value, resolver)
                    : value;
            if (!Objects.equals(value, newValue)) {
                changed = true;
            }
            resolved.put(entry.getKey(), newValue);
        }

        if (!changed) {
            return this;
        }
        return fromMap(resolved);
    }

    private static String replacePlaceholders(String value, LegacyVariableResolver resolver) {
        if (value == null || value.indexOf('$') < 0) {
            return value;
        }
        Matcher matcher = LegacyVariableResolver.PLACEHOLDER_PATTERN.matcher(value);
        if (!matcher.find()) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        int last = 0;
        boolean changed = false;
        do {
            String path = matcher.group(1);
            String replacement = resolver.resolve(path);
            if (replacement != null) {
                sb.append(value, last, matcher.start());
                sb.append(replacement);
                last = matcher.end();
                changed = true;
            }
        } while (matcher.find());

        if (!changed) {
            return value;
        }
        sb.append(value, last, value.length());
        return sb.toString();
    }

    public static final class Builder {
        private String resourceId;
        private Pattern resourceIdPattern;
        private String text;
        private String textContains;
        private String textStartsWith;
        private Pattern textPattern;
        private String contentDescription;
        private String contentDescriptionContains;
        private String contentDescriptionStartsWith;
        private Pattern contentDescriptionPattern;
        private String className;
        private Pattern classNamePattern;
        private String packageName;
        private Pattern packageNamePattern;
        private Boolean clickable;
        private Boolean enabled;
        private Boolean selected;
        private Boolean checkable;
        private Boolean checked;
        private Boolean focusable;
        private Boolean focused;
        private Boolean scrollable;
        private Boolean longClickable;
        private long timeoutMs = 1200L;
        private Map<String, Object> rawConfig = Collections.emptyMap();

        private Builder() {
        }

        public Builder resourceId(String value) {
            this.resourceId = value;
            return this;
        }

        public Builder resourceIdMatches(String pattern) {
            this.resourceIdPattern = Pattern.compile(pattern);
            return this;
        }

        public Builder text(String value) {
            this.text = value;
            return this;
        }

        public Builder textContains(String value) {
            this.textContains = value;
            return this;
        }

        public Builder textStartsWith(String value) {
            this.textStartsWith = value;
            return this;
        }

        public Builder textMatches(String pattern) {
            this.textPattern = Pattern.compile(pattern);
            return this;
        }

        public Builder contentDescription(String value) {
            this.contentDescription = value;
            return this;
        }

        public Builder contentDescriptionContains(String value) {
            this.contentDescriptionContains = value;
            return this;
        }

        public Builder contentDescriptionStartsWith(String value) {
            this.contentDescriptionStartsWith = value;
            return this;
        }

        public Builder contentDescriptionMatches(String pattern) {
            this.contentDescriptionPattern = Pattern.compile(pattern);
            return this;
        }

        public Builder className(String value) {
            this.className = value;
            return this;
        }

        public Builder classNameMatches(String pattern) {
            this.classNamePattern = Pattern.compile(pattern);
            return this;
        }

        public Builder packageName(String value) {
            this.packageName = value;
            return this;
        }

        public Builder packageNameMatches(String pattern) {
            this.packageNamePattern = Pattern.compile(pattern);
            return this;
        }

        public Builder clickable(boolean value) {
            this.clickable = value;
            return this;
        }

        public Builder enabled(boolean value) {
            this.enabled = value;
            return this;
        }

        public Builder selected(boolean value) {
            this.selected = value;
            return this;
        }

        public Builder checkable(boolean value) {
            this.checkable = value;
            return this;
        }

        public Builder checked(boolean value) {
            this.checked = value;
            return this;
        }

        public Builder focusable(boolean value) {
            this.focusable = value;
            return this;
        }

        public Builder focused(boolean value) {
            this.focused = value;
            return this;
        }

        public Builder scrollable(boolean value) {
            this.scrollable = value;
            return this;
        }

        public Builder longClickable(boolean value) {
            this.longClickable = value;
            return this;
        }

        public Builder timeoutMs(long value) {
            this.timeoutMs = value;
            return this;
        }

        public Builder rawConfig(Map<String, Object> rawConfig) {
            if (rawConfig == null || rawConfig.isEmpty()) {
                this.rawConfig = Collections.emptyMap();
            } else {
                this.rawConfig = Collections.unmodifiableMap(new LinkedHashMap<>(rawConfig));
            }
            return this;
        }

        public SelectorCondition build() {
            BySelector selector = createSelector();
            return new SelectorCondition(
                    selector,
                    timeoutMs,
                    resourceId,
                    resourceIdPattern,
                    text,
                    textContains,
                    textStartsWith,
                    textPattern,
                    contentDescription,
                    contentDescriptionContains,
                    contentDescriptionStartsWith,
                    contentDescriptionPattern,
                    className,
                    classNamePattern,
                    packageName,
                    packageNamePattern,
                    clickable,
                    enabled,
                    selected,
                    checkable,
                    checked,
                    focusable,
                    focused,
                    scrollable,
                    longClickable,
                    rawConfig
            );
        }

        private BySelector createSelector() {
            BySelector selector = null;
            if (resourceId != null) {
                selector = By.res(resourceId);
            } else if (resourceIdPattern != null) {
                selector = By.res(resourceIdPattern);
            }
            if (text != null) {
                selector = selector == null ? By.text(text) : selector.text(text);
            }
            if (textContains != null) {
                selector = selector == null ? By.textContains(textContains) : selector.textContains(textContains);
            }
            if (textStartsWith != null) {
                selector = selector == null ? By.textStartsWith(textStartsWith) : selector.textStartsWith(textStartsWith);
            }
            if (textPattern != null) {
                selector = selector == null ? By.text(textPattern) : selector.text(textPattern);
            }
            if (contentDescription != null) {
                selector = selector == null ? By.desc(contentDescription) : selector.desc(contentDescription);
            }
            if (contentDescriptionContains != null) {
                selector = selector == null ? By.descContains(contentDescriptionContains) : selector.descContains(contentDescriptionContains);
            }
            if (contentDescriptionStartsWith != null) {
                selector = selector == null ? By.descStartsWith(contentDescriptionStartsWith) : selector.descStartsWith(contentDescriptionStartsWith);
            }
            if (contentDescriptionPattern != null) {
                selector = selector == null ? By.desc(contentDescriptionPattern) : selector.desc(contentDescriptionPattern);
            }
            if (className != null) {
                selector = selector == null ? By.clazz(className) : selector.clazz(className);
            }
            if (classNamePattern != null) {
                selector = selector == null ? By.clazz(classNamePattern) : selector.clazz(classNamePattern);
            }
            if (packageName != null) {
                selector = selector == null ? By.pkg(packageName) : selector.pkg(packageName);
            }
            if (packageNamePattern != null) {
                selector = selector == null ? By.pkg(packageNamePattern) : selector.pkg(packageNamePattern);
            }
            if (clickable != null) {
                selector = selector == null ? By.clickable(clickable) : selector.clickable(clickable);
            }
            if (enabled != null) {
                selector = selector == null ? By.enabled(enabled) : selector.enabled(enabled);
            }
            if (selected != null) {
                selector = selector == null ? By.selected(selected) : selector.selected(selected);
            }
            if (checkable != null) {
                selector = selector == null ? By.checkable(checkable) : selector.checkable(checkable);
            }
            if (checked != null) {
                selector = selector == null ? By.checked(checked) : selector.checked(checked);
            }
            if (focusable != null) {
                selector = selector == null ? By.focusable(focusable) : selector.focusable(focusable);
            }
            if (focused != null) {
                selector = selector == null ? By.focused(focused) : selector.focused(focused);
            }
            if (scrollable != null) {
                selector = selector == null ? By.scrollable(scrollable) : selector.scrollable(scrollable);
            }
            if (longClickable != null) {
                selector = selector == null ? By.longClickable(longClickable) : selector.longClickable(longClickable);
            }
            if (selector == null) {
                throw new IllegalStateException("SelectorCondition requires at least one attribute");
            }
            return selector;
        }
    }
}
