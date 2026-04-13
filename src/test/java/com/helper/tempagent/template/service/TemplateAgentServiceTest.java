package com.helper.tempagent.template.service;

import com.helper.tempagent.template.export.ExportFormat;
import com.helper.tempagent.template.export.ExportResult;
import com.helper.tempagent.template.export.ExportService;
import com.helper.tempagent.template.ai.AiParamResolver;
import com.helper.tempagent.template.ai.DeterministicPlanningTool;
import com.helper.tempagent.template.ai.SpringAiPlanningToolContract;
import com.helper.tempagent.template.ai.TemplatePlanGuardrail;
import com.helper.tempagent.template.ingestion.TemplateParserAdapter;
import com.helper.tempagent.template.model.TemplateDocument;
import com.helper.tempagent.template.model.TextNode;
import com.helper.tempagent.template.orchestration.ApiBinding;
import com.helper.tempagent.template.orchestration.ApiInvocationPipeline;
import com.helper.tempagent.template.orchestration.BindingRegistry;
import com.helper.tempagent.template.orchestration.OrchestrationException;
import com.helper.tempagent.template.orchestration.ResponseNormalizer;
import com.helper.tempagent.template.render.TemplateRenderer;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateAgentServiceTest {

    @Test
    void shouldThrowWhenNoDataProvidedForPlaceholders() {
        TemplateParserAdapter parser = content -> new TemplateDocument(List.of(new TextNode("Hello {{name}}")), List.of("name"));
        BindingRegistry registry = new BindingRegistry() {
            @Override
            public Optional<ApiBinding> findByPlaceholder(String placeholder) {
                return Optional.empty();
            }

            @Override
            public Map all() {
                return Map.of();
            }
        };
        com.helper.tempagent.config.TemplateEngineProperties props = new com.helper.tempagent.config.TemplateEngineProperties();
        props.setPlaceholderPattern("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}");
        props.setMissingValuePolicy(com.helper.tempagent.config.MissingValuePolicy.KEEP_TOKEN);
        TemplateRenderer renderer = new TemplateRenderer(props);
        TemplateAgentService service = new TemplateAgentService(
                parser, registry, new ApiInvocationPipeline(new com.fasterxml.jackson.databind.ObjectMapper()),
                new ResponseNormalizer(), renderer,
                (rendered, format) -> new ExportResult("id", format, rendered, Instant.now()),
                Optional.<SpringAiPlanningToolContract>empty(),
                Optional.<AiParamResolver>empty(),
                new DeterministicPlanningTool(),
                new TemplatePlanGuardrail(),
                new SimpleMeterRegistry()
        );
        assertThrows(OrchestrationException.class,
                () -> service.renderAndExport(new RenderTemplateRequest("ignored", ExportFormat.HTML, null)));
    }

    @Test
    void shouldRenderWithInlineData() {
        TemplateParserAdapter parser = content -> new TemplateDocument(List.of(new TextNode("Hello {{name}}")), List.of("name"));
        BindingRegistry registry = new BindingRegistry() {
            @Override
            public Optional<ApiBinding> findByPlaceholder(String placeholder) {
                return Optional.empty();
            }

            @Override
            public Map all() {
                return Map.of();
            }
        };
        com.helper.tempagent.config.TemplateEngineProperties props = new com.helper.tempagent.config.TemplateEngineProperties();
        props.setPlaceholderPattern("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}");
        props.setMissingValuePolicy(com.helper.tempagent.config.MissingValuePolicy.KEEP_TOKEN);
        TemplateRenderer renderer = new TemplateRenderer(props);
        ExportService exportService = (rendered, format) -> new ExportResult("id", format, rendered, Instant.now());
        TemplateAgentService service = new TemplateAgentService(
                parser, registry, new ApiInvocationPipeline(new com.fasterxml.jackson.databind.ObjectMapper()),
                new ResponseNormalizer(), renderer, exportService,
                Optional.<SpringAiPlanningToolContract>empty(),
                Optional.<AiParamResolver>empty(),
                new DeterministicPlanningTool(),
                new TemplatePlanGuardrail(),
                new SimpleMeterRegistry()
        );
        ExportResult result = service.renderAndExport(new RenderTemplateRequest("ignored", ExportFormat.HTML, Map.of("name", "Bob")));
        assertEquals("Hello Bob", result.content());
    }
}
