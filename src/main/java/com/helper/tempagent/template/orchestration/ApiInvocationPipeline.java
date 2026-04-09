package com.helper.tempagent.template.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ApiInvocationPipeline {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiHttpExecutor httpExecutor;

    public ApiInvocationPipeline(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
        this.httpExecutor = binding -> restClient.method(HttpMethod.valueOf(binding.method().toUpperCase()))
                .uri(binding.endpoint())
                .retrieve()
                .body(String.class);
    }

    ApiInvocationPipeline(ObjectMapper objectMapper, ApiHttpExecutor httpExecutor) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
        this.httpExecutor = httpExecutor;
    }

    public JsonNode invoke(ApiBinding binding) {
        RuntimeException lastException = null;
        // 对瞬时失败做轻量重试，保持行为可控且有上限。
        for (int i = 0; i < 3; i++) {
            try {
                String response = httpExecutor.execute(binding);
                return objectMapper.readTree(response);
            } catch (Exception ex) {
                lastException = ex instanceof RuntimeException re ? re : new RuntimeException(ex);
            }
        }
        throw new OrchestrationException("Failed to invoke API for placeholder: " + binding.placeholderPath(), lastException);
    }
}
