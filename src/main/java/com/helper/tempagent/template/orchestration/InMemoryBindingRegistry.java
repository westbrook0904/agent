package com.helper.tempagent.template.orchestration;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryBindingRegistry implements BindingRegistry {

    private final Map<String, ApiBinding> bindings = new ConcurrentHashMap<>();

    @Override
    public Optional<ApiBinding> findByPlaceholder(String placeholder) {
        return Optional.ofNullable(bindings.get(placeholder));
    }

    @Override
    public Map<String, ApiBinding> all() {
        return Map.copyOf(bindings);
    }

    public void register(ApiBinding binding) {
        bindings.put(binding.placeholderPath(), binding);
    }
}
