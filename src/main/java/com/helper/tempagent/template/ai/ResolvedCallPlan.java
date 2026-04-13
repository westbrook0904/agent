package com.helper.tempagent.template.ai;

import java.util.List;
import java.util.Map;

public record ResolvedCallPlan(
        List<String> orderedPlaceholders,
        Map<String, Map<String, String>> resolvedParams
) {

    public ResolvedCallPlan {
        if (orderedPlaceholders == null) {
            orderedPlaceholders = List.of();
        }
        if (resolvedParams == null) {
            resolvedParams = Map.of();
        }
    }

    public static ResolvedCallPlan fromPlanWithoutParams(ApiCallPlan plan) {
        return new ResolvedCallPlan(plan.orderedPlaceholders(), Map.of());
    }

    public Map<String, String> paramsFor(String placeholder) {
        return resolvedParams.getOrDefault(placeholder, Map.of());
    }
}
