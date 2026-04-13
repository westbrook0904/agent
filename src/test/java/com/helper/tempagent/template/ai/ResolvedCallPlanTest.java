package com.helper.tempagent.template.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResolvedCallPlanTest {

    @Test
    void shouldCreateWithResolvedParams() {
        var plan = new ResolvedCallPlan(
                List.of("reportMeta", "salesData"),
                Map.of(
                        "reportMeta", Map.of("month", "2026-03"),
                        "salesData", Map.of("month", "2026-03", "pageSize", "50")
                )
        );
        assertEquals(List.of("reportMeta", "salesData"), plan.orderedPlaceholders());
        assertEquals(Map.of("month", "2026-03"), plan.paramsFor("reportMeta"));
        assertEquals(Map.of("month", "2026-03", "pageSize", "50"), plan.paramsFor("salesData"));
    }

    @Test
    void shouldReturnEmptyParamsForUnknownPlaceholder() {
        var plan = new ResolvedCallPlan(List.of("a"), Map.of("a", Map.of("k", "v")));
        assertEquals(Map.of(), plan.paramsFor("unknown"));
    }

    @Test
    void shouldCreateFromPlanWithoutParams() {
        var apiCallPlan = new ApiCallPlan(List.of("x", "y"));
        var resolved = ResolvedCallPlan.fromPlanWithoutParams(apiCallPlan);
        assertEquals(List.of("x", "y"), resolved.orderedPlaceholders());
        assertEquals(Map.of(), resolved.resolvedParams());
        assertEquals(Map.of(), resolved.paramsFor("x"));
    }

    @Test
    void shouldDefaultNullsToEmptyCollections() {
        var plan = new ResolvedCallPlan(null, null);
        assertEquals(List.of(), plan.orderedPlaceholders());
        assertEquals(Map.of(), plan.resolvedParams());
    }
}
