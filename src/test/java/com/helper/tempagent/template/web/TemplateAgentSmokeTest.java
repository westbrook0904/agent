package com.helper.tempagent.template.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helper.tempagent.template.export.ExportFormat;
import com.helper.tempagent.template.service.RenderTemplateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TemplateAgentSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRenderTemplateViaApi() throws Exception {
        RenderTemplateRequest request = new RenderTemplateRequest(
                "Hello {{name}}",
                ExportFormat.HTML,
                Map.of("name", "Smoke")
        );

        mockMvc.perform(post("/api/template-agent/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("HTML"))
                .andExpect(jsonPath("$.content").value("Hello Smoke"));
    }
}
