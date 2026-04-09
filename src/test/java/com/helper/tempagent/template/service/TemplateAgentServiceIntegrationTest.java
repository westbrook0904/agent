package com.helper.tempagent.template.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helper.tempagent.config.MissingValuePolicy;
import com.helper.tempagent.config.TemplateEngineProperties;
import com.helper.tempagent.template.ai.DeterministicPlanningTool;
import com.helper.tempagent.template.ai.SpringAiPlanningToolContract;
import com.helper.tempagent.template.ai.TemplatePlanGuardrail;
import com.helper.tempagent.template.export.ExportFormat;
import com.helper.tempagent.template.export.ExportResult;
import com.helper.tempagent.template.export.ExportService;
import com.helper.tempagent.template.ingestion.PlaceholderTokenizer;
import com.helper.tempagent.template.ingestion.SimpleTemplateParserAdapter;
import com.helper.tempagent.template.ingestion.TemplateParserAdapter;
import com.helper.tempagent.template.orchestration.*;
import com.helper.tempagent.template.render.TemplateRenderer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateAgentServiceIntegrationTest {

    @Test
    void shouldFallbackToDeterministicPlanWhenAiPlannerFails() {
        TemplateEngineProperties props = new TemplateEngineProperties();
        props.setPlaceholderPattern("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}");
        props.setEachStartPattern("\\{\\{#each\\s+([a-zA-Z0-9_.-]+)\\}\\}");
        props.setEachEndToken("{{/each}}");
        props.setMissingValuePolicy(MissingValuePolicy.KEEP_TOKEN);

        TemplateParserAdapter parser = new SimpleTemplateParserAdapter(new PlaceholderTokenizer(props), props);
        InMemoryBindingRegistry registry = new InMemoryBindingRegistry();
        registry.register(new ApiBinding("customer.name", "http://fake", "GET", "data.name", Map.of()));

        ApiHttpExecutor executor = binding -> "{\"data\":{\"name\":\"Alice\"}}";
        ApiInvocationPipeline pipeline = new ApiInvocationPipeline(new ObjectMapper(), executor);
        TemplateRenderer renderer = new TemplateRenderer(props);
        ExportService exportService = (rendered, format) -> new ExportResult("id", format, rendered, Instant.now());
        SpringAiPlanningToolContract brokenAiTool = (document, bindingRegistry) -> {
            throw new IllegalStateException("ai unavailable");
        };

        TemplateAgentService service = new TemplateAgentService(
                parser,
                registry,
                pipeline,
                new ResponseNormalizer(),
                renderer,
                exportService,
                Optional.of(brokenAiTool),
                new DeterministicPlanningTool(),
                new TemplatePlanGuardrail(),
                new SimpleMeterRegistry()
        );

        ExportResult result = service.renderAndExport(new RenderTemplateRequest(
                "Hello {{customer.name}}",
                ExportFormat.HTML,
                Map.of()
        ));
        assertEquals("Hello Alice", result.content());
    }
}
