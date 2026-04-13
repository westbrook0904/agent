package com.helper.tempagent.template.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helper.tempagent.template.orchestration.ApiBinding;
import com.helper.tempagent.template.orchestration.BindingRegistry;
import com.helper.tempagent.template.orchestration.OrchestrationException;
import com.helper.tempagent.template.orchestration.ParamSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Component
@ConditionalOnProperty(prefix = "tempagent.ai", name = "enabled", havingValue = "true")
@ConditionalOnBean(ChatClient.Builder.class)
public class SpringAiParamResolver implements AiParamResolver {

    private static final Logger log = LoggerFactory.getLogger(SpringAiParamResolver.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SpringAiParamResolver(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    SpringAiParamResolver(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResolvedCallPlan resolve(ApiCallPlan plan, String userIntent, BindingRegistry registry) {
        Map<String, List<ParamSchema>> schemasByPlaceholder = new LinkedHashMap<>();
        Map<String, ApiBinding> bindingsByPlaceholder = new LinkedHashMap<>();

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

        log.info("AI param resolution starting, placeholders={}", schemasByPlaceholder.keySet());
        String prompt = buildPrompt(schemasByPlaceholder, userIntent);
        Map<String, Map<String, String>> rawResolved = callAi(prompt);
        Map<String, Map<String, String>> validated = validate(rawResolved, schemasByPlaceholder, bindingsByPlaceholder);
        log.info("AI param resolution complete, resolvedPlaceholders={}", validated.keySet());

        return new ResolvedCallPlan(plan.orderedPlaceholders(), validated);
    }

    private String buildPrompt(Map<String, List<ParamSchema>> schemasByPlaceholder, String userIntent) {
        var schemasDesc = new StringJoiner("\n");
        schemasByPlaceholder.forEach((placeholder, schemas) -> {
            schemasDesc.add("Placeholder: " + placeholder);
            for (var s : schemas) {
                String line = "  - %s (type: %s, required: %s)".formatted(s.name(), s.type(), s.required());
                if (s.description() != null && !s.description().isBlank()) {
                    line += " — " + s.description();
                }
                if (s.defaultValue() != null) {
                    line += " [default: " + s.defaultValue() + "]";
                }
                if (!s.enumValues().isEmpty()) {
                    line += " [allowed: " + String.join(", ", s.enumValues()) + "]";
                }
                schemasDesc.add(line);
            }
        });

        return """
                You are an API parameter resolver. Given a user's intent and API parameter schemas, \
                produce the correct parameter values.
                
                Return ONLY strict JSON in this format (no explanation, no markdown):
                {"<placeholderPath>": {"<paramName>": "<value>", ...}, ...}
                
                Parameter schemas:
                %s
                
                User intent: "%s"
                """.formatted(schemasDesc, userIntent);
    }

    private Map<String, Map<String, String>> callAi(String prompt) {
        try {
            String content = chatClient.prompt().user(prompt).call().content();
            JsonNode root = objectMapper.readTree(content);
            Map<String, Map<String, String>> result = new LinkedHashMap<>();
            for (var entry : root.properties()) {
                Map<String, String> params = objectMapper.convertValue(
                        entry.getValue(), new TypeReference<>() {});
                result.put(entry.getKey(), params);
            }
            return result;
        } catch (Exception ex) {
            log.warn("AI param resolution failed, returning empty resolution: {}", ex.getMessage());
            return Map.of();
        }
    }

    Map<String, Map<String, String>> validate(
            Map<String, Map<String, String>> rawResolved,
            Map<String, List<ParamSchema>> schemasByPlaceholder,
            Map<String, ApiBinding> bindingsByPlaceholder) {

        Map<String, Map<String, String>> validated = new LinkedHashMap<>();

        schemasByPlaceholder.forEach((placeholder, schemas) -> {
            Map<String, String> resolvedForPlaceholder = rawResolved.getOrDefault(placeholder, Map.of());
            ApiBinding binding = bindingsByPlaceholder.get(placeholder);
            Map<String, String> staticParams = binding != null ? binding.requestParams() : Map.of();
            Map<String, String> validatedParams = new LinkedHashMap<>();

            for (ParamSchema schema : schemas) {
                String resolved = resolvedForPlaceholder.get(schema.name());

                if (resolved != null && isValidValue(resolved, schema)) {
                    validatedParams.put(schema.name(), resolved);
                } else if (resolved != null) {
                    log.warn("Validation failed for param '{}' on '{}': value='{}', falling back to static",
                            schema.name(), placeholder, resolved);
                    String fallback = staticParams.get(schema.name());
                    if (fallback != null) {
                        validatedParams.put(schema.name(), fallback);
                    } else if (schema.defaultValue() != null) {
                        validatedParams.put(schema.name(), schema.defaultValue());
                    } else if (schema.required()) {
                        throw new OrchestrationException(
                                "Required parameter '%s' for placeholder '%s' could not be resolved"
                                        .formatted(schema.name(), placeholder));
                    }
                } else {
                    if (schema.defaultValue() != null) {
                        validatedParams.put(schema.name(), schema.defaultValue());
                    } else if (staticParams.containsKey(schema.name())) {
                        validatedParams.put(schema.name(), staticParams.get(schema.name()));
                    } else if (schema.required()) {
                        throw new OrchestrationException(
                                "Required parameter '%s' for placeholder '%s' was not resolved and has no default"
                                        .formatted(schema.name(), placeholder));
                    }
                }
            }

            validated.put(placeholder, validatedParams);
        });

        return validated;
    }

    private boolean isValidValue(String value, ParamSchema schema) {
        return switch (schema.type()) {
            case INTEGER -> {
                try {
                    Integer.parseInt(value);
                    yield true;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case BOOLEAN -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            case DATE -> {
                try {
                    YearMonth.parse(value);
                    yield true;
                } catch (DateTimeParseException e) {
                    yield value.matches("\\d{4}-\\d{2}(-\\d{2})?");
                }
            }
            case ENUM -> schema.enumValues().contains(value);
            case STRING -> true;
        };
    }
}
