package com.helper.tempagent.template.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
