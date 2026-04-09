package com.helper.tempagent.template.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseNormalizerTest {

    @Test
    void shouldExtractNestedValueByResponsePath() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ResponseNormalizer normalizer = new ResponseNormalizer();
        var raw = mapper.readTree("{\"data\":{\"customer\":{\"name\":\"Bob\"}}}");
        var result = normalizer.normalize(
                Map.of("customer.name", raw),
                Map.of("customer.name", new ApiBinding("customer.name", "x", "GET", "data.customer.name", Map.of()))
        );
        assertEquals("Bob", result.get("customer.name"));
    }
}
