package com.helper.tempagent.template.orchestration;

import java.util.List;

public record ParamSchema(
        String name,
        ParamType type,
        String description,
        boolean required,
        String defaultValue,
        List<String> enumValues
) {

    public enum ParamType {
        STRING, INTEGER, DATE, BOOLEAN, ENUM
    }

    public ParamSchema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ParamSchema name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("ParamSchema type must not be null");
        }
        if (type == ParamType.ENUM && (enumValues == null || enumValues.isEmpty())) {
            throw new IllegalArgumentException("ENUM type requires non-empty enumValues");
        }
        if (enumValues == null) {
            enumValues = List.of();
        }
    }
}
