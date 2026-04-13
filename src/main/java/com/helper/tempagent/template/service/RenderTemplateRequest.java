package com.helper.tempagent.template.service;

import com.helper.tempagent.template.export.ExportFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record RenderTemplateRequest(
        @NotBlank String templateContent,
        @NotNull ExportFormat format,
        Map<String, Object> inlineData,
        String userIntent
) {

    public RenderTemplateRequest(String templateContent, ExportFormat format, Map<String, Object> inlineData) {
        this(templateContent, format, inlineData, null);
    }

    public boolean hasUserIntent() {
        return userIntent != null && !userIntent.isBlank();
    }
}
