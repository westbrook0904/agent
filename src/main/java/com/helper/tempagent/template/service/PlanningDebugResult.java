package com.helper.tempagent.template.service;

import com.helper.tempagent.template.ai.ReActTraceEntry;

import java.util.List;

public record PlanningDebugResult(
        List<String> rawPlan,
        List<String> guardedPlan,
        List<ReActTraceEntry> trace
) {
}
