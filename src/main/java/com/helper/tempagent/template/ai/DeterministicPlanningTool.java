package com.helper.tempagent.template.ai;

import com.helper.tempagent.template.model.TemplateDocument;
import com.helper.tempagent.template.orchestration.BindingRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DeterministicPlanningTool {
    public ApiCallPlan plan(TemplateDocument document, BindingRegistry registry) {
        List<String> placeholders = new ArrayList<>(document.placeholders());
        return new ApiCallPlan(placeholders);
    }
}
