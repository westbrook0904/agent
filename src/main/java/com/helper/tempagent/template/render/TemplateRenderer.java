package com.helper.tempagent.template.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.helper.tempagent.config.MissingValuePolicy;
import com.helper.tempagent.config.TemplateEngineProperties;
import com.helper.tempagent.template.model.EachBlockNode;
import com.helper.tempagent.template.model.TemplateDocument;
import com.helper.tempagent.template.model.TemplateNode;
import com.helper.tempagent.template.model.TextNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateRenderer {

    private final Pattern placeholderPattern;
    private final MissingValuePolicy missingValuePolicy;

    public TemplateRenderer(TemplateEngineProperties properties) {
        this.placeholderPattern = Pattern.compile(properties.getPlaceholderPattern());
        this.missingValuePolicy = properties.getMissingValuePolicy();
    }

    public String render(TemplateDocument document, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        // 按节点顺序渲染，保证模板原始结构顺序不变。
        for (TemplateNode node : document.nodes()) {
            if (node instanceof TextNode textNode) {
                sb.append(replacePlaceholders(textNode.text(), context));
            } else if (node instanceof EachBlockNode eachNode) {
                sb.append(renderEach(eachNode, context));
            }
        }
        return sb.toString();
    }

    private String renderEach(EachBlockNode eachNode, Map<String, Object> context) {
        Object value = context.get(eachNode.collectionPath());
        // 集合缺失或非数组时，按设计返回空展开结果。
        if (!(value instanceof JsonNode jsonNode) || !jsonNode.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : jsonNode) {
            sb.append(replaceWithItem(eachNode.body(), item));
        }
        return sb.toString();
    }

    private String replaceWithItem(String text, JsonNode item) {
        Matcher matcher = placeholderPattern.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(1);
            JsonNode valueNode = path.startsWith("item.") ? item.path(path.substring("item.".length())) : null;
            String value = valueNode != null && !valueNode.isMissingNode() ? valueNode.asText() : resolveMissing("{{" + path + "}}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String replacePlaceholders(String text, Map<String, Object> context) {
        Matcher matcher = placeholderPattern.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = context.get(key);
            String replacement = value != null ? String.valueOf(value) : resolveMissing("{{" + key + "}}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveMissing(String token) {
        // 缺失值策略可配置，以适配严格或宽松业务场景。
        return switch (missingValuePolicy) {
            case EMPTY -> "";
            case FAIL -> throw new IllegalStateException("Missing value for token: " + token);
            case KEEP_TOKEN -> token;
        };
    }
}
