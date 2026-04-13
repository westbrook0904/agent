package com.helper.tempagent.template.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class ApiInvocationPipeline {

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiHttpExecutor httpExecutor;

    public ApiInvocationPipeline(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
        this.httpExecutor = new DefaultHttpExecutor(restClient, objectMapper);
    }

    public ApiInvocationPipeline(ObjectMapper objectMapper, ApiHttpExecutor httpExecutor) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
        this.httpExecutor = httpExecutor;
    }

    public JsonNode invoke(ApiBinding binding) {
        return invoke(binding, Map.of());
    }

    public JsonNode invoke(ApiBinding binding, Map<String, String> aiResolvedParams) {
        Map<String, String> mergedParams = mergeParams(binding.requestParams(), aiResolvedParams);
        RuntimeException lastException = null;
        for (int i = 0; i < 3; i++) {
            try {
                String response = mergedParams.isEmpty()
                        ? httpExecutor.execute(binding)
                        : httpExecutor.execute(binding, mergedParams);
                return objectMapper.readTree(response);
            } catch (Exception ex) {
                lastException = ex instanceof RuntimeException re ? re : new RuntimeException(ex);
            }
        }
        throw new OrchestrationException("Failed to invoke API for placeholder: " + binding.placeholderPath(), lastException);
    }

    static Map<String, String> mergeParams(Map<String, String> staticParams, Map<String, String> aiParams) {
        if (staticParams.isEmpty() && aiParams.isEmpty()) {
            return Map.of();
        }
        var merged = new LinkedHashMap<>(staticParams);
        merged.putAll(aiParams);
        return Map.copyOf(merged);
    }

    private static class DefaultHttpExecutor implements ApiHttpExecutor {
        private final RestClient restClient;
        private final ObjectMapper objectMapper;

        DefaultHttpExecutor(RestClient restClient, ObjectMapper objectMapper) {
            this.restClient = restClient;
            this.objectMapper = objectMapper;
        }

        @Override
        public String execute(ApiBinding binding) {
            return execute(binding, Map.of());
        }

        @Override
        public String execute(ApiBinding binding, Map<String, String> mergedParams) {
            String method = binding.method().toUpperCase();
            if (BODY_METHODS.contains(method)) {
                return executeWithBody(binding, mergedParams);
            }
            return executeWithQuery(binding, mergedParams);
        }

        private String executeWithQuery(ApiBinding binding, Map<String, String> params) {
            String uri = binding.endpoint();
            if (!params.isEmpty()) {
                var builder = UriComponentsBuilder.fromUriString(uri);
                params.forEach(builder::queryParam);
                uri = builder.build().toUriString();
            }
            return restClient.method(HttpMethod.valueOf(binding.method().toUpperCase()))
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
        }

        private String executeWithBody(ApiBinding binding, Map<String, String> params) {
            try {
                String body = params.isEmpty() ? "{}" : objectMapper.writeValueAsString(params);
                return restClient.method(HttpMethod.valueOf(binding.method().toUpperCase()))
                        .uri(binding.endpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to serialize request body", ex);
            }
        }
    }
}
