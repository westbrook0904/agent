package com.helper.tempagent.template.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helper.tempagent.template.export.ExportFormat;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenderTemplateRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldDeserializeWithUserIntent() throws Exception {
        String json = """
                {"templateContent":"hello","format":"HTML","userIntent":"generate March report"}
                """;
        var request = mapper.readValue(json, RenderTemplateRequest.class);
        assertEquals("generate March report", request.userIntent());
        assertTrue(request.hasUserIntent());
    }

    @Test
    void shouldDeserializeWithoutUserIntent() throws Exception {
        String json = """
                {"templateContent":"hello","format":"HTML"}
                """;
        var request = mapper.readValue(json, RenderTemplateRequest.class);
        assertNull(request.userIntent());
        assertFalse(request.hasUserIntent());
    }

    @Test
    void shouldTreatBlankIntentAsAbsent() {
        var request = new RenderTemplateRequest("hello", ExportFormat.HTML, null, "   ");
        assertFalse(request.hasUserIntent());
    }

    @Test
    void shouldTreatNullIntentAsAbsent() {
        var request = new RenderTemplateRequest("hello", ExportFormat.HTML, null);
        assertFalse(request.hasUserIntent());
    }

    @Test
    void shouldPreserveBackwardCompatibility() {
        var request = new RenderTemplateRequest("hello", ExportFormat.HTML, Map.of("k", "v"));
        assertNull(request.userIntent());
        assertFalse(request.hasUserIntent());
        assertEquals(Map.of("k", "v"), request.inlineData());
    }
}
