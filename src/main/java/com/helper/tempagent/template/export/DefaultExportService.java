package com.helper.tempagent.template.export;

import com.helper.tempagent.template.trace.ExportTraceStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class DefaultExportService implements ExportService {

    private final ExportTraceStore traceStore;

    public DefaultExportService(ExportTraceStore traceStore) {
        this.traceStore = traceStore;
    }

    @Override
    public ExportResult export(String rendered, ExportFormat format) {
        if (format != ExportFormat.HTML && format != ExportFormat.EML) {
            throw new UnsupportedOperationException("Unsupported format: " + format);
        }
        String renderId = UUID.randomUUID().toString();
        ExportResult result = new ExportResult(renderId, format, rendered, Instant.now());
        traceStore.recordSuccess(result);
        return result;
    }
}
