package com.helper.tempagent.template.ai;

import com.helper.tempagent.template.model.TemplateDocument;
import com.helper.tempagent.template.orchestration.BindingRegistry;

public interface SpringAiPlanningToolContract {
    ApiCallPlan plan(TemplateDocument document, BindingRegistry registry);
}
