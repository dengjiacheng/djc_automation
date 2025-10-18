package com.automation.domain.command;

import java.util.Locale;

/**
 * Supported parameter types for command validation.
 */
public enum ParameterType {
    STRING,
    INT,
    FLOAT,
    BOOL,
    OBJECT,
    ARRAY,
    FILE,
    IMAGE,
    JSON,
    ENUM;

    public static ParameterType fromRaw(String raw) {
        if (raw == null || raw.isEmpty()) {
            return STRING;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "int", "integer" -> INT;
            case "float", "double", "number" -> FLOAT;
            case "bool", "boolean" -> BOOL;
            case "object", "map", "dict" -> OBJECT;
            case "array", "list" -> ARRAY;
            case "file" -> FILE;
            case "image" -> IMAGE;
            case "json" -> JSON;
            case "enum" -> ENUM;
            default -> STRING;
        };
    }
}
