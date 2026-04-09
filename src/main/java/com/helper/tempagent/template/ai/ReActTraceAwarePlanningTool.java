package com.helper.tempagent.template.ai;

import java.util.List;

public interface ReActTraceAwarePlanningTool {
    List<ReActTraceEntry> lastTrace();
}
