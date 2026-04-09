package com.helper.tempagent.template.ingestion;

import com.helper.tempagent.template.model.TemplateDocument;

public interface TemplateParserAdapter {
    TemplateDocument parse(String content);
}
