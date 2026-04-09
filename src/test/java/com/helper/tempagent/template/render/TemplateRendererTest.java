package com.helper.tempagent.template.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helper.tempagent.config.MissingValuePolicy;
import com.helper.tempagent.config.TemplateEngineProperties;
import com.helper.tempagent.template.model.EachBlockNode;
import com.helper.tempagent.template.model.TemplateDocument;
import com.helper.tempagent.template.model.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateRendererTest {

    private TemplateRenderer renderer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TemplateEngineProperties properties = new TemplateEngineProperties();
        properties.setPlaceholderPattern("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}");
        properties.setMissingValuePolicy(MissingValuePolicy.KEEP_TOKEN);
        renderer = new TemplateRenderer(properties);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldRenderScalarAndCollection() {
        TemplateDocument doc = new TemplateDocument(
                List.of(
                        new TextNode("Hello {{customer.name}}\n"),
                        new EachBlockNode("orders", "| {{item.id}} |\n")
                ),
                List.of("customer.name", "orders", "item.id")
        );
        String output = renderer.render(doc, Map.of(
                "customer.name", "Alice",
                "orders", objectMapper.valueToTree(List.of(Map.of("id", "A1"), Map.of("id", "B2")))
        ));
        assertTrue(output.contains("Hello Alice"));
        assertTrue(output.contains("| A1 |"));
        assertTrue(output.contains("| B2 |"));
    }

    @Test
    void shouldKeepTableStructureAndStyleContent() {
        TemplateDocument doc = new TemplateDocument(
                List.of(
                        new TextNode("<table><tr style=\"font-weight:bold\"><td>Name</td></tr>"),
                        new EachBlockNode("orders", "<tr><td>{{item.id}}</td></tr>"),
                        new TextNode("</table>")
                ),
                List.of("orders", "item.id")
        );
        String output = renderer.render(doc, Map.of(
                "orders", objectMapper.valueToTree(List.of(Map.of("id", "A1"), Map.of("id", "B2")))
        ));
        assertTrue(output.contains("style=\"font-weight:bold\""));
        assertTrue(output.startsWith("<table>"));
        assertTrue(output.endsWith("</table>"));
        assertEquals(3, output.split("<tr>").length - 1);
    }
}
