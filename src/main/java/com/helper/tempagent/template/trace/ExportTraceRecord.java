package com.helper.tempagent.template.trace;

import com.helper.tempagent.template.export.ExportFormat;

import java.time.Instant;

public record ExportTraceRecord(
        String renderId,
        ExportFormat format,
        Instant timestamp,
        boolean success,
        String message
) {
}
