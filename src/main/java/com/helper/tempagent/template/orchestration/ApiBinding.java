package com.helper.tempagent.template.orchestration;

import java.util.Map;

public record ApiBinding(
        String placeholderPath,
        String endpoint,
        String method,
        String responsePath,
        Map<String, String> requestParams
) {
}
