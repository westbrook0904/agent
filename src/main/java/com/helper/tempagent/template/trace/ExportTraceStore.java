package com.helper.tempagent.template.trace;

import com.helper.tempagent.template.export.ExportResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ExportTraceStore {

    private final List<ExportTraceRecord> records = new CopyOnWriteArrayList<>();

    public void recordSuccess(ExportResult result) {
        records.add(new ExportTraceRecord(result.renderId(), result.format(), result.exportedAt(), true, "OK"));
    }

    public void recordFailure(String renderId, String message) {
        records.add(new ExportTraceRecord(renderId, null, java.time.Instant.now(), false, message));
    }

    public List<ExportTraceRecord> all() {
        return List.copyOf(records);
    }
}
