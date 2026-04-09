package com.helper.tempagent.template.service;

import jakarta.validation.constraints.NotBlank;

public record PlanningDebugRequest(@NotBlank String templateContent) {
}
