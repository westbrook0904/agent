package com.helper.tempagent.template.ai;

import com.helper.tempagent.template.model.TemplateDocument;
import com.helper.tempagent.template.orchestration.BindingRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class TemplatePlanGuardrail {

    public ApiCallPlan enforce(ApiCallPlan input, TemplateDocument document, BindingRegistry registry) {
        // 仅允许模板中声明过的占位符通过。
        Set<String> inTemplate = new LinkedHashSet<>(document.placeholders());
        Set<String> allowed = new LinkedHashSet<>();
        for (String placeholder : input.orderedPlaceholders()) {
            // 同时要求存在绑定，避免模型生成任意调用。
            if (inTemplate.contains(placeholder) && registry.findByPlaceholder(placeholder).isPresent()) {
                allowed.add(placeholder);
            }
        }
        return new ApiCallPlan(allowed.stream().toList());
    }
}
