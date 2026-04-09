package com.helper.tempagent.template.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.helper.tempagent.template.export.ExportResult;
import com.helper.tempagent.template.export.ExportService;
import com.helper.tempagent.template.ai.ApiCallPlan;
import com.helper.tempagent.template.ai.DeterministicPlanningTool;
import com.helper.tempagent.template.ai.ReActTraceAwarePlanningTool;
import com.helper.tempagent.template.ai.ReActTraceEntry;
import com.helper.tempagent.template.ai.SpringAiPlanningToolContract;
import com.helper.tempagent.template.ai.TemplatePlanGuardrail;
import com.helper.tempagent.template.ingestion.TemplateParserAdapter;
import com.helper.tempagent.template.model.TemplateDocument;
import com.helper.tempagent.template.orchestration.ApiBinding;
import com.helper.tempagent.template.orchestration.ApiInvocationPipeline;
import com.helper.tempagent.template.orchestration.BindingRegistry;
import com.helper.tempagent.template.orchestration.OrchestrationException;
import com.helper.tempagent.template.orchestration.ResponseNormalizer;
import com.helper.tempagent.template.render.TemplateRenderer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TemplateAgentService {

    private static final Logger log = LoggerFactory.getLogger(TemplateAgentService.class);

    private final TemplateParserAdapter parserAdapter;
    private final BindingRegistry bindingRegistry;
    private final ApiInvocationPipeline invocationPipeline;
    private final ResponseNormalizer responseNormalizer;
    private final TemplateRenderer renderer;
    private final ExportService exportService;
    private final Optional<SpringAiPlanningToolContract> aiPlanningTool;
    private final DeterministicPlanningTool deterministicPlanningTool;
    private final TemplatePlanGuardrail guardrail;
    private final MeterRegistry meterRegistry;

    public TemplateAgentService(
            TemplateParserAdapter parserAdapter,
            BindingRegistry bindingRegistry,
            ApiInvocationPipeline invocationPipeline,
            ResponseNormalizer responseNormalizer,
            TemplateRenderer renderer,
            ExportService exportService,
            Optional<SpringAiPlanningToolContract> aiPlanningTool,
            DeterministicPlanningTool deterministicPlanningTool,
            TemplatePlanGuardrail guardrail,
            MeterRegistry meterRegistry
    ) {
        this.parserAdapter = parserAdapter;
        this.bindingRegistry = bindingRegistry;
        this.invocationPipeline = invocationPipeline;
        this.responseNormalizer = responseNormalizer;
        this.renderer = renderer;
        this.exportService = exportService;
        this.aiPlanningTool = aiPlanningTool;
        this.deterministicPlanningTool = deterministicPlanningTool;
        this.guardrail = guardrail;
        this.meterRegistry = meterRegistry;
    }

    public ExportResult renderAndExport(RenderTemplateRequest request) {
        // 统计一次渲染与导出的端到端耗时。
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("template pipeline started, format={}", request.format());
        TemplateDocument document = parserAdapter.parse(request.templateContent());
        meterRegistry.counter("tempagent.ingestion.requests").increment();
        log.debug("template parsed, placeholders={}", document.placeholders());

        Map<String, JsonNode> rawByPlaceholder = new LinkedHashMap<>();
        Map<String, ApiBinding> resolvedBindings = new LinkedHashMap<>();
        // 先生成调用计划，再仅执行通过校验的占位符。
        ApiCallPlan plan = resolvePlan(document);
        for (String placeholder : plan.orderedPlaceholders()) {
            ApiBinding binding = bindingRegistry.findByPlaceholder(placeholder).orElse(null);
            if (binding == null) {
                continue;
            }
            resolvedBindings.put(placeholder, binding);
            rawByPlaceholder.put(placeholder, invocationPipeline.invoke(binding));
        }
        meterRegistry.counter("tempagent.orchestration.calls").increment(rawByPlaceholder.size());
        log.debug("orchestration completed, calls={}", rawByPlaceholder.size());

        // inlineData 优先级高于 API 数据，可用于快速覆盖。
        Map<String, Object> context = new LinkedHashMap<>(responseNormalizer.normalize(rawByPlaceholder, resolvedBindings));
        if (request.inlineData() != null) {
            context.putAll(request.inlineData());
        }

        if (context.isEmpty() && !document.placeholders().isEmpty()) {
            throw new OrchestrationException("No data available for placeholders: " + document.placeholders());
        }

        String rendered = renderer.render(document, context);
        meterRegistry.counter("tempagent.render.requests").increment();
        ExportResult result = exportService.export(rendered, request.format());
        meterRegistry.counter("tempagent.export.requests").increment();
        sample.stop(meterRegistry.timer("tempagent.pipeline.duration"));
        log.info("template pipeline finished, renderId={}, format={}", result.renderId(), result.format());
        return result;
    }

    public PlanningDebugResult debugPlanning(String templateContent) {
        TemplateDocument document = parserAdapter.parse(templateContent);
        ApiCallPlan rawPlan = aiPlanningTool
                .map(tool -> tool.plan(document, bindingRegistry))
                .orElseGet(() -> deterministicPlanningTool.plan(document, bindingRegistry));
        ApiCallPlan guarded = guardrail.enforce(rawPlan, document, bindingRegistry);
        List<ReActTraceEntry> trace = aiPlanningTool
                .filter(tool -> tool instanceof ReActTraceAwarePlanningTool)
                .map(tool -> ((ReActTraceAwarePlanningTool) tool).lastTrace())
                .orElse(List.of());
        return new PlanningDebugResult(rawPlan.orderedPlaceholders(), guarded.orderedPlaceholders(), trace);
    }

    private ApiCallPlan resolvePlan(TemplateDocument document) {
        try {
            // 启用 AI 时优先使用 AI 规划，否则走确定性规划。
            ApiCallPlan planned = aiPlanningTool
                    .map(tool -> tool.plan(document, bindingRegistry))
                    .orElseGet(() -> deterministicPlanningTool.plan(document, bindingRegistry));
            return guardrail.enforce(planned, document, bindingRegistry);
        } catch (Exception ex) {
            // 规划失败不能中断主流程，必须回退到确定性规划。
            log.warn("AI planning failed, fallback to deterministic planner: {}", ex.getMessage());
            return guardrail.enforce(deterministicPlanningTool.plan(document, bindingRegistry), document, bindingRegistry);
        }
    }
}
