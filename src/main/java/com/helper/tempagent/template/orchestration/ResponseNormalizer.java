package com.helper.tempagent.template.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ResponseNormalizer {

    public Map<String, Object> normalize(Map<String, JsonNode> rawByPlaceholder, Map<String, ApiBinding> resolvedBindings) {
        Map<String, Object> context = new LinkedHashMap<>();
        rawByPlaceholder.forEach((placeholder, raw) -> {
            ApiBinding binding = resolvedBindings.get(placeholder);
            JsonNode extracted = extractByPath(raw, binding == null ? null : binding.responsePath());
            context.put(placeholder, extracted.isValueNode() ? extracted.asText() : extracted);
        });
        return context;
    }

    private JsonNode extractByPath(JsonNode source, String responsePath) {
        if (responsePath == null || responsePath.isBlank()) {
            return source;
        }
        String[] parts = responsePath.split("\\.");
        JsonNode current = source;
        for (String part : parts) {
            if (current == null) {
                break;
            }
            current = current.path(part);
        }
        return current == null ? source : current;
    }
}
