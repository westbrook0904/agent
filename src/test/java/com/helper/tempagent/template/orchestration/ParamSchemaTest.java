package com.helper.tempagent.template.orchestration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParamSchemaTest {

    @Test
    void shouldCreateValidParamSchema() {
        var schema = new ParamSchema("month", ParamSchema.ParamType.DATE,
                "Report month in yyyy-MM format", true, null, List.of());
        assertEquals("month", schema.name());
        assertEquals(ParamSchema.ParamType.DATE, schema.type());
        assertTrue(schema.required());
        assertNull(schema.defaultValue());
    }

    @Test
    void shouldCreateEnumSchemaWithValues() {
        var schema = new ParamSchema("format", ParamSchema.ParamType.ENUM,
                "Output format", false, "summary", List.of("summary", "detail"));
        assertEquals(List.of("summary", "detail"), schema.enumValues());
        assertEquals("summary", schema.defaultValue());
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParamSchema("", ParamSchema.ParamType.STRING, "desc", false, null, List.of()));
    }

    @Test
    void shouldRejectNullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParamSchema("name", null, "desc", false, null, List.of()));
    }

    @Test
    void shouldRejectEnumWithoutValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParamSchema("format", ParamSchema.ParamType.ENUM, "desc", false, null, List.of()));
    }

    @Test
    void shouldDefaultNullEnumValuesToEmptyList() {
        var schema = new ParamSchema("name", ParamSchema.ParamType.STRING, "desc", false, null, null);
        assertEquals(List.of(), schema.enumValues());
    }

    @Test
    void shouldCreateApiBindingWithParameterSchemas() {
        var schemas = List.of(
                new ParamSchema("month", ParamSchema.ParamType.DATE, "Report month", true, null, List.of()),
                new ParamSchema("format", ParamSchema.ParamType.ENUM, "Format", false, "summary", List.of("summary", "detail"))
        );
        var binding = new ApiBinding("report", "http://api/report", "GET", "data", Map.of(), schemas);
        assertEquals(2, binding.parameterSchemas().size());
        assertEquals("month", binding.parameterSchemas().get(0).name());
    }

    @Test
    void shouldCreateApiBindingWithoutSchemas() {
        var binding = new ApiBinding("report", "http://api/report", "GET", "data", Map.of());
        assertEquals(List.of(), binding.parameterSchemas());
    }

    @Test
    void shouldDefaultNullParameterSchemasToEmptyList() {
        var binding = new ApiBinding("report", "http://api/report", "GET", "data", Map.of(), null);
        assertEquals(List.of(), binding.parameterSchemas());
    }
}
