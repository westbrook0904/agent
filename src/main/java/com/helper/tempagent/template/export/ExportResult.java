package com.helper.tempagent.template.export;

import java.time.Instant;

public record ExportResult(
        String renderId,
        ExportFormat format,
        String content,
        Instant exportedAt
) {
}
