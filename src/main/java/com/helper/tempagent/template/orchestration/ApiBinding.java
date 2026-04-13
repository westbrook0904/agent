package com.helper.tempagent.template.orchestration;

import java.util.List;
import java.util.Map;

public record ApiBinding(
        String placeholderPath,
        String endpoint,
        String method,
        String responsePath,
        Map<String, String> requestParams,
        List<ParamSchema> parameterSchemas
) {

    public ApiBinding(String placeholderPath, String endpoint, String method,
                      String responsePath, Map<String, String> requestParams) {
        this(placeholderPath, endpoint, method, responsePath, requestParams, List.of());
    }

    public ApiBinding {
        if (parameterSchemas == null) {
            parameterSchemas = List.of();
        }
    }
}
