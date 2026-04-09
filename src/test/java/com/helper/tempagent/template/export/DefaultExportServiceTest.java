package com.helper.tempagent.template.export;

import com.helper.tempagent.template.trace.ExportTraceStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultExportServiceTest {

    @Test
    void shouldRecordSuccessTrace() {
        ExportTraceStore traceStore = new ExportTraceStore();
        DefaultExportService service = new DefaultExportService(traceStore);
        ExportResult result = service.export("<html/>", ExportFormat.HTML);
        assertEquals(ExportFormat.HTML, result.format());
        assertEquals(1, traceStore.all().size());
        assertEquals(true, traceStore.all().getFirst().success());
    }

    @Test
    void shouldRejectUnsupportedFormat() {
        ExportTraceStore traceStore = new ExportTraceStore();
        DefaultExportService service = new DefaultExportService(traceStore);
        assertThrows(UnsupportedOperationException.class, () -> service.export("body", null));
    }
}
