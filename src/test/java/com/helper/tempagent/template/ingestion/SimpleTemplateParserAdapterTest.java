package com.helper.tempagent.template.ingestion;

import com.helper.tempagent.config.MissingValuePolicy;
import com.helper.tempagent.config.TemplateEngineProperties;
import com.helper.tempagent.template.model.TemplateDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleTemplateParserAdapterTest {

    private SimpleTemplateParserAdapter parserAdapter;

    @BeforeEach
    void setUp() {
        TemplateEngineProperties properties = new TemplateEngineProperties();
        properties.setPlaceholderPattern("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}");
        properties.setEachStartPattern("\\{\\{#each\\s+([a-zA-Z0-9_.-]+)\\}\\}");
        properties.setEachEndToken("{{/each}}");
        properties.setMissingValuePolicy(MissingValuePolicy.KEEP_TOKEN);
        parserAdapter = new SimpleTemplateParserAdapter(new PlaceholderTokenizer(properties), properties);
    }

    @Test
    void shouldParseTextAndEachBlock() {
        String content = "Hello {{customer.name}}\n{{#each orders}}| {{item.id}} |\n{{/each}}";
        TemplateDocument doc = parserAdapter.parse(content);
        assertTrue(doc.placeholders().contains("customer.name"));
        assertTrue(doc.placeholders().contains("orders"));
        assertTrue(doc.placeholders().contains("item.id"));
    }

    @Test
    void shouldFailWhenEachEndMissing() {
        String content = "{{#each orders}}|{{item.id}}|";
        TemplateValidationException ex = assertThrows(TemplateValidationException.class, () -> parserAdapter.parse(content));
        assertEquals("Missing {{/each}} token for collection: orders", ex.getMessage());
    }
}
