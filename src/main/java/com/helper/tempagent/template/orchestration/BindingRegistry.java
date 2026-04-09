package com.helper.tempagent.template.orchestration;

import java.util.Map;
import java.util.Optional;

public interface BindingRegistry {
    Optional<ApiBinding> findByPlaceholder(String placeholder);

    Map<String, ApiBinding> all();
}
