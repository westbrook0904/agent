package com.helper.tempagent.template.ai;

import com.helper.tempagent.template.orchestration.BindingRegistry;

public interface AiParamResolver {

    ResolvedCallPlan resolve(ApiCallPlan plan, String userIntent, BindingRegistry registry);
}
