package com.helper.tempagent.template.web;

import com.helper.tempagent.template.export.ExportResult;
import com.helper.tempagent.template.service.PlanningDebugRequest;
import com.helper.tempagent.template.service.PlanningDebugResult;
import com.helper.tempagent.template.service.RenderTemplateRequest;
import com.helper.tempagent.template.service.TemplateAgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/template-agent")
public class TemplateAgentController {

    private final TemplateAgentService templateAgentService;

    public TemplateAgentController(TemplateAgentService templateAgentService) {
        this.templateAgentService = templateAgentService;
    }

    @PostMapping("/render")
    public ExportResult render(@Valid @RequestBody RenderTemplateRequest request) {
        return templateAgentService.renderAndExport(request);
    }

    @PostMapping("/planning/debug")
    public PlanningDebugResult planningDebug(@Valid @RequestBody PlanningDebugRequest request) {
        return templateAgentService.debugPlanning(request.templateContent());
    }
}
