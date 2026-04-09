package com.helper.tempagent.template.model;

import java.util.List;

public record TemplateDocument(List<TemplateNode> nodes, List<String> placeholders) {
}
