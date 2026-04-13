package com.helper.tempagent.template.orchestration;

import java.util.Map;

public interface ApiHttpExecutor {
    String execute(ApiBinding binding);

    default String execute(ApiBinding binding, Map<String, String> mergedParams) {
        return execute(binding);
    }
}
