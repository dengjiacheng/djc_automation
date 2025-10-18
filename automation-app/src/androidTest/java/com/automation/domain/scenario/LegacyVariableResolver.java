package com.automation.domain.scenario;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 基于 Map 的变量解析器，通过点号路径取值并支持占位符替换。
 */
public final class LegacyVariableResolver {

    public static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([\\w.]+)\\}");
    private static final char PATH_SEPARATOR = '.';
    private static final LegacyVariableResolver EMPTY =
            new LegacyVariableResolver(Collections.<String, Object>emptyMap());

    private final Map<String, Object> data;

    private LegacyVariableResolver(Map<String, Object> data) {
        this.data = data;
    }

    public static LegacyVariableResolver empty() {
        return EMPTY;
    }

    public static LegacyVariableResolver from(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return EMPTY;
        }
        return new LegacyVariableResolver(snapshot(values));
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public String resolve(String path) {
        if (path == null || path.length() == 0) {
            return null;
        }
        if (path.charAt(0) == PATH_SEPARATOR || path.charAt(path.length() - 1) == PATH_SEPARATOR) {
            return null;
        }

        Object current = data;
        int segmentStart = 0;

        while (true) {
            int segmentEnd = path.indexOf(PATH_SEPARATOR, segmentStart);
            boolean lastSegment = segmentEnd == -1;
            String key = lastSegment
                    ? path.substring(segmentStart)
                    : path.substring(segmentStart, segmentEnd);

            if (!isValidKey(key)) {
                return null;
            }
            if (!(current instanceof Map)) {
                return null;
            }

            current = ((Map<?, ?>) current).get(key);
            if (current == null) {
                return null;
            }

            if (lastSegment) {
                break;
            }

            segmentStart = segmentEnd + 1;
        }

        return current.toString();
    }

    private static boolean isValidKey(String key) {
        return key != null && key.length() != 0;
    }

    private static Map<String, Object> snapshot(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
