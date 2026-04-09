package com.helper.tempagent.template.ai;

public record ReActTraceEntry(
        int step,
        String thought,
        String action,
        String observation
) {
}
