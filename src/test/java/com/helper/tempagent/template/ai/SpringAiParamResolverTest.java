package com.helper.tempagent.template.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helper.tempagent.template.orchestration.ApiBinding;
import com.helper.tempagent.template.orchestration.BindingRegistry;
import com.helper.tempagent.template.orchestration.OrchestrationException;
import com.helper.tempagent.template.orchestration.ParamSchema;
import com.helper.tempagent.template.orchestration.ParamSchema.ParamType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SpringAiParamResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPassValidationForCorrectValues() {
        var schemas = Map.of("report", List.of(
                new ParamSchema("month", ParamType.DATE, "yyyy-MM", true, null, List.of()),
                new ParamSchema("format", ParamType.ENUM, "format", false, "summary", List.of("summary", "detail"))
        ));
        var bindings = Map.of("report", new ApiBinding("report", "http://x", "GET", "", Map.of()));
        var raw = Map.of("report", Map.of("month", "2026-03", "format", "detail"));

        var resolver = new TestableResolver(objectMapper);
        var validated = resolver.validate(raw, schemas, bindings);

        assertEquals("2026-03", validated.get("report").get("month"));
        assertEquals("detail", validated.get("report").get("format"));
    }

    @Test
    void shouldFallBackToStaticOnValidationFailure() {
        var schemas = Map.of("report", List.of(
                new ParamSchema("month", ParamType.DATE, "yyyy-MM", true, null, List.of())
        ));
        var bindings = Map.of("report",
                new ApiBinding("report", "http://x", "GET", "", Map.of("month", "2026-01")));
        var raw = Map.of("report", Map.of("month", "last quarter"));

        var resolver = new TestableResolver(objectMapper);
        var validated = resolver.validate(raw, schemas, bindings);

        assertEquals("2026-01", validated.get("report").get("month"));
    }

    @Test
    void shouldUseDefaultWhenNotResolved() {
        var schemas = Map.of("report", List.of(
                new ParamSchema("format", ParamType.ENUM, "format", false, "summary", List.of("summary", "detail"))
        ));
        var bindings = Map.of("report", new ApiBinding("report", "http://x", "GET", "", Map.of()));
        var raw = Map.<String, Map<String, String>>of("report", Map.of());

        var resolver = new TestableResolver(objectMapper);
        var validated = resolver.validate(raw, schemas, bindings);

        assertEquals("summary", validated.get("report").get("format"));
    }

    @Test
    void shouldThrowOnMissingRequiredParam() {
        var schemas = Map.of("report", List.of(
                new ParamSchema("month", ParamType.DATE, "yyyy-MM", true, null, List.of())
        ));
        var bindings = Map.of("report", new ApiBinding("report", "http://x", "GET", "", Map.of()));
        var raw = Map.<String, Map<String, String>>of("report", Map.of());

        var resolver = new TestableResolver(objectMapper);
        assertThrows(OrchestrationException.class,
                () -> resolver.validate(raw, schemas, bindings));
    }

    @Test
    void shouldRejectInvalidEnumValue() {
        var schemas = Map.of("report", List.of(
                new ParamSchema("format", ParamType.ENUM, "format", true, null, List.of("summary", "detail"))
        ));
        var bindings = Map.of("report",
                new ApiBinding("report", "http://x", "GET", "", Map.of("format", "summary")));
        var raw = Map.of("report", Map.of("format", "invalid"));

        var resolver = new TestableResolver(objectMapper);
        var validated = resolver.validate(raw, schemas, bindings);

        assertEquals("summary", validated.get("report").get("format"));
    }

    @Test
    void shouldValidateIntegerType() {
        var schemas = Map.of("data", List.of(
                new ParamSchema("pageSize", ParamType.INTEGER, "page size", false, "20", List.of())
        ));
        var bindings = Map.of("data", new ApiBinding("data", "http://x", "GET", "", Map.of()));
        var raw = Map.of("data", Map.of("pageSize", "abc"));

        var resolver = new TestableResolver(objectMapper);
        var validated = resolver.validate(raw, schemas, bindings);

        assertEquals("20", validated.get("data").get("pageSize"));
    }

    @Test
    void shouldValidateBooleanType() {
        var schemas = Map.of("data", List.of(
                new ParamSchema("active", ParamType.BOOLEAN, "filter", false, null, List.of())
        ));
        var bindings = Map.of("data", new ApiBinding("data", "http://x", "GET", "", Map.of()));
        var raw = Map.of("data", Map.of("active", "true"));

        var resolver = new TestableResolver(objectMapper);
        var validated = resolver.validate(raw, schemas, bindings);

        assertEquals("true", validated.get("data").get("active"));
    }

    @Test
    void shouldSkipResolutionWhenNoSchemas() {
        BindingRegistry registry = new BindingRegistry() {
            @Override
            public Optional<ApiBinding> findByPlaceholder(String p) {
                return Optional.of(new ApiBinding(p, "http://x", "GET", "", Map.of()));
            }
            @Override
            public Map<String, ApiBinding> all() { return Map.of(); }
        };
        var resolver = new TestableResolver(objectMapper, "{}");
        var plan = new ApiCallPlan(List.of("simple"));
        var resolved = resolver.resolve(plan, "some intent", registry);

        assertEquals(List.of("simple"), resolved.orderedPlaceholders());
        assertEquals(Map.of(), resolved.resolvedParams());
    }

    @Test
    void shouldResolveMultipleBindingsFromSingleCall() {
        var monthSchema = new ParamSchema("month", ParamType.STRING, "Report month", true, null, List.of());
        BindingRegistry registry = new BindingRegistry() {
            @Override
            public Optional<ApiBinding> findByPlaceholder(String p) {
                return Optional.of(new ApiBinding(p, "http://x", "GET", "", Map.of(), List.of(monthSchema)));
            }
            @Override
            public Map<String, ApiBinding> all() { return Map.of(); }
        };

        String aiResponse = """
                {"reportMeta":{"month":"2026-03"},"salesData":{"month":"2026-03"}}
                """;
        var resolver = new TestableResolver(objectMapper, aiResponse);
        var plan = new ApiCallPlan(List.of("reportMeta", "salesData"));
        var resolved = resolver.resolve(plan, "generate the March report", registry);

        assertEquals("2026-03", resolved.paramsFor("reportMeta").get("month"));
        assertEquals("2026-03", resolved.paramsFor("salesData").get("month"));
    }

    /**
     * Subclass that exposes validate() and overrides the AI call for testing.
     */
    static class TestableResolver extends SpringAiParamResolver {
        private final String aiResponse;

        TestableResolver(ObjectMapper objectMapper) {
            this(objectMapper, "{}");
        }

        TestableResolver(ObjectMapper objectMapper, String aiResponse) {
            super((org.springframework.ai.chat.client.ChatClient) null, objectMapper);
            this.aiResponse = aiResponse;
        }

        @Override
        public ResolvedCallPlan resolve(ApiCallPlan plan, String userIntent, BindingRegistry registry) {
            var schemasByPlaceholder = new java.util.LinkedHashMap<String, List<ParamSchema>>();
            var bindingsByPlaceholder = new java.util.LinkedHashMap<String, ApiBinding>();

            for (String placeholder : plan.orderedPlaceholders()) {
                registry.findByPlaceholder(placeholder).ifPresent(binding -> {
                    if (!binding.parameterSchemas().isEmpty()) {
                        schemasByPlaceholder.put(placeholder, binding.parameterSchemas());
                        bindingsByPlaceholder.put(placeholder, binding);
                    }
                });
            }

            if (schemasByPlaceholder.isEmpty()) {
                return ResolvedCallPlan.fromPlanWithoutParams(plan);
            }

            try {
                var objectMapper = new ObjectMapper();
                var root = objectMapper.readTree(aiResponse);
                var rawResolved = new java.util.LinkedHashMap<String, Map<String, String>>();
                for (var entry : root.properties()) {
                    Map<String, String> params = objectMapper.convertValue(
                            entry.getValue(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                    rawResolved.put(entry.getKey(), params);
                }
                return new ResolvedCallPlan(plan.orderedPlaceholders(), validate(rawResolved, schemasByPlaceholder, bindingsByPlaceholder));
            } catch (Exception e) {
                return ResolvedCallPlan.fromPlanWithoutParams(plan);
            }
        }
    }
}
