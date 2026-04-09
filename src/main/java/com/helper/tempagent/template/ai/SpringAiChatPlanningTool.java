package com.helper.tempagent.template.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helper.tempagent.template.model.TemplateDocument;
import com.helper.tempagent.template.orchestration.BindingRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "tempagent.ai", name = "enabled", havingValue = "true")
@ConditionalOnBean(ChatClient.Builder.class)
public class SpringAiChatPlanningTool implements SpringAiPlanningToolContract, ReActTraceAwarePlanningTool {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    // 每次请求都记录一份 ReAct 轨迹，便于 debug 接口读取当前线程内的步骤信息。
    private final ThreadLocal<List<ReActTraceEntry>> traceHolder = ThreadLocal.withInitial(ArrayList::new);

    public SpringAiChatPlanningTool(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public ApiCallPlan plan(TemplateDocument document, BindingRegistry registry) {
        // 1) 读取当前可用的绑定集合，模型只能在这些 placeholder 中选择动作目标。
        Map<String, ?> bindings = registry.all();

        // 2) 初始化本次规划轨迹，覆盖同线程上一次调用的数据。
        List<ReActTraceEntry> trace = new ArrayList<>();
        traceHolder.set(trace);

        // 3) remaining: 还未选择的占位符；chosen: 已确认进入调用计划的占位符。
        Set<String> remaining = new LinkedHashSet<>(document.placeholders());
        Set<String> chosen = new LinkedHashSet<>();

        // 4) 限制最大推理步数，防止模型陷入无效循环。
        int maxSteps = 6;
        for (int step = 1; step <= maxSteps; step++) {
            // 5) 每一步都让模型返回结构化动作，禁止自然语言解释，降低解析不确定性。
            String prompt = """
                    You are a ReAct planner for API placeholder resolution.
                    Return ONLY strict JSON:
                    {"thought":"...","action":"CALL_API|FINISH","placeholder":"optional"}

                    Allowed placeholders:
                    %s
                    Already chosen placeholders:
                    %s
                    Remaining placeholders:
                    %s
                    Available binding placeholders:
                    %s
                    """.formatted(document.placeholders(), chosen, remaining, bindings.keySet());

            // 6) 调用模型，解析动作对象（thought/action/placeholder）。
            String content = chatClient.prompt().user(prompt).call().content();
            JsonNode actionNode = safeParse(content);
            String thought = actionNode.path("thought").asText("n/a");
            String action = actionNode.path("action").asText("FINISH");
            String placeholder = actionNode.path("placeholder").asText("");

            // 7) 仅当动作合法且目标在 remaining 且存在绑定时，才接受本步选择。
            if ("CALL_API".equalsIgnoreCase(action)
                    && remaining.contains(placeholder)
                    && bindings.containsKey(placeholder)) {
                chosen.add(placeholder);
                remaining.remove(placeholder);
                trace.add(new ReActTraceEntry(step, thought, "CALL_API:" + placeholder, "selected"));
            } else {
                // 8) FINISH 或非法动作统一视为终止，并把观察结果写入轨迹。
                trace.add(new ReActTraceEntry(step, thought, action, "finish-or-invalid"));
                break;
            }

            // 9) 全部占位符都已选完时提前结束，不再继续调用模型。
            if (remaining.isEmpty()) {
                break;
            }
        }

        // 10) 若模型至少选出一个合法步骤，则返回该计划；否则退回模板原始顺序兜底。
        if (!chosen.isEmpty()) {
            return new ApiCallPlan(new ArrayList<>(chosen));
        }
        return new ApiCallPlan(new ArrayList<>(document.placeholders()));
    }

    private JsonNode safeParse(String raw) {
        try {
            // 模型输出必须是 JSON；这里做容错解析，避免异常中断主流程。
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            // 返回空对象后，外层会自动走 FINISH/非法动作分支并触发兜底计划。
            return objectMapper.createObjectNode();
        }
    }

    @Override
    public List<ReActTraceEntry> lastTrace() {
        // 返回不可变副本，避免调用方篡改内部轨迹。
        return List.copyOf(traceHolder.get());
    }
}
