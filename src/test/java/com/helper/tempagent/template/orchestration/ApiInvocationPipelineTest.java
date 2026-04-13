package com.helper.tempagent.template.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ApiInvocationPipelineTest {

    @Test
    void shouldRetryTransientFailureAndSucceed() {
        AtomicInteger calls = new AtomicInteger();
        ApiHttpExecutor executor = binding -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return "{\"data\":{\"name\":\"Alice\"}}";
        };
        ApiInvocationPipeline pipeline = new ApiInvocationPipeline(new ObjectMapper(), executor);
        var json = pipeline.invoke(new ApiBinding("customer.name", "http://localhost", "GET", "data.name", Map.of()));
        assertEquals("Alice", json.path("data").path("name").asText());
        assertEquals(3, calls.get());
    }

    @Test
    void shouldFailAfterMaxRetry() {
        ApiHttpExecutor executor = binding -> {
            throw new RuntimeException("always fail");
        };
        ApiInvocationPipeline pipeline = new ApiInvocationPipeline(new ObjectMapper(), executor);
        assertThrows(OrchestrationException.class,
                () -> pipeline.invoke(new ApiBinding("customer.name", "http://localhost", "GET", "data.name", Map.of())));
    }

    @Test
    void shouldMergeParamsWithAiPriority() {
        var merged = ApiInvocationPipeline.mergeParams(
                Map.of("apiKey", "xyz", "month", "2026-01"),
                Map.of("month", "2026-03")
        );
        assertEquals("xyz", merged.get("apiKey"));
        assertEquals("2026-03", merged.get("month"));
    }

    @Test
    void shouldReturnEmptyWhenBothEmpty() {
        var merged = ApiInvocationPipeline.mergeParams(Map.of(), Map.of());
        assertTrue(merged.isEmpty());
    }

    @Test
    void shouldPassMergedParamsToExecutor() {
        AtomicReference<Map<String, String>> capturedParams = new AtomicReference<>();
        ApiHttpExecutor executor = new ApiHttpExecutor() {
            @Override
            public String execute(ApiBinding binding) {
                return execute(binding, Map.of());
            }

            @Override
            public String execute(ApiBinding binding, Map<String, String> mergedParams) {
                capturedParams.set(mergedParams);
                return "{\"ok\":true}";
            }
        };
        ApiInvocationPipeline pipeline = new ApiInvocationPipeline(new ObjectMapper(), executor);
        pipeline.invoke(
                new ApiBinding("report", "http://x", "GET", "", Map.of("apiKey", "abc")),
                Map.of("month", "2026-03")
        );
        var params = capturedParams.get();
        assertEquals("abc", params.get("apiKey"));
        assertEquals("2026-03", params.get("month"));
    }

    @Test
    void shouldInvokeWithEmptyParamsWhenNoneProvided() {
        AtomicReference<String> capturedExecType = new AtomicReference<>();
        ApiHttpExecutor executor = new ApiHttpExecutor() {
            @Override
            public String execute(ApiBinding binding) {
                capturedExecType.set("no-params");
                return "{\"ok\":true}";
            }

            @Override
            public String execute(ApiBinding binding, Map<String, String> mergedParams) {
                capturedExecType.set("with-params");
                return "{\"ok\":true}";
            }
        };
        ApiInvocationPipeline pipeline = new ApiInvocationPipeline(new ObjectMapper(), executor);
        pipeline.invoke(new ApiBinding("x", "http://x", "GET", "", Map.of()));
        assertEquals("no-params", capturedExecType.get());
    }
}
