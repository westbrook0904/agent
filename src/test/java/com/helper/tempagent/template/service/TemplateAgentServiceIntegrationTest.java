package com.helper.tempagent.template.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helper.tempagent.config.MissingValuePolicy;
import com.helper.tempagent.config.TemplateEngineProperties;
import com.helper.tempagent.template.ai.AiParamResolver;
import com.helper.tempagent.template.ai.DeterministicPlanningTool;
import com.helper.tempagent.template.ai.ResolvedCallPlan;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                Optional.<AiParamResolver>empty(),
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

    @Test
    void shouldResolveParamsFromUserIntentAndRenderCollectionTable() {
        TemplateEngineProperties props = new TemplateEngineProperties();
        props.setPlaceholderPattern("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}");
        props.setEachStartPattern("\\{\\{#each\\s+([a-zA-Z0-9_.-]+)\\}\\}");
        props.setEachEndToken("{{/each}}");
        props.setMissingValuePolicy(MissingValuePolicy.KEEP_TOKEN);

        TemplateParserAdapter parser = new SimpleTemplateParserAdapter(new PlaceholderTokenizer(props), props);
        InMemoryBindingRegistry registry = new InMemoryBindingRegistry();

        var monthSchema = new ParamSchema("month", ParamSchema.ParamType.STRING, "Report month yyyy-MM", true, null, List.of());
        registry.register(new ApiBinding("reportTitle", "http://fake/meta", "GET", "title",
                Map.of(), List.of(monthSchema)));
        registry.register(new ApiBinding("salesData", "http://fake/sales", "GET", "items",
                Map.of(), List.of(monthSchema)));

        AtomicReference<Map<String, String>> capturedMetaParams = new AtomicReference<>();
        AtomicReference<Map<String, String>> capturedSalesParams = new AtomicReference<>();

        ApiHttpExecutor executor = new ApiHttpExecutor() {
            @Override
            public String execute(ApiBinding binding) { return execute(binding, Map.of()); }

            @Override
            public String execute(ApiBinding binding, Map<String, String> mergedParams) {
                if (binding.placeholderPath().equals("reportTitle")) {
                    capturedMetaParams.set(mergedParams);
                    return "{\"title\":\"March 2026 Sales Report\"}";
                }
                capturedSalesParams.set(mergedParams);
                return "{\"items\":[{\"product\":\"Widget\",\"amount\":\"100\"},{\"product\":\"Gadget\",\"amount\":\"250\"}]}";
            }
        };

        AiParamResolver fakeResolver = (plan, userIntent, reg) -> new ResolvedCallPlan(
                plan.orderedPlaceholders(),
                Map.of(
                        "reportTitle", Map.of("month", "2026-03"),
                        "salesData", Map.of("month", "2026-03")
                )
        );

        ApiInvocationPipeline pipeline = new ApiInvocationPipeline(new ObjectMapper(), executor);
        TemplateRenderer renderer = new TemplateRenderer(props);
        ExportService exportService = (rendered, format) -> new ExportResult("id", format, rendered, Instant.now());

        TemplateAgentService service = new TemplateAgentService(
                parser, registry, pipeline, new ResponseNormalizer(), renderer, exportService,
                Optional.<SpringAiPlanningToolContract>empty(),
                Optional.of(fakeResolver),
                new DeterministicPlanningTool(),
                new TemplatePlanGuardrail(),
                new SimpleMeterRegistry()
        );

        String template = """
                <h1>{{reportTitle}}</h1>\
                <table>{{#each salesData}}<tr><td>{{item.product}}</td><td>{{item.amount}}</td></tr>{{/each}}</table>""";

        ExportResult result = service.renderAndExport(new RenderTemplateRequest(
                template, ExportFormat.HTML, null, "generate the March sales report"
        ));

        assertEquals("2026-03", capturedMetaParams.get().get("month"));
        assertEquals("2026-03", capturedSalesParams.get().get("month"));

        String content = result.content();
        assertTrue(content.contains("March 2026 Sales Report"));
        assertTrue(content.contains("<td>Widget</td><td>100</td>"));
        assertTrue(content.contains("<td>Gadget</td><td>250</td>"));
    }
}
