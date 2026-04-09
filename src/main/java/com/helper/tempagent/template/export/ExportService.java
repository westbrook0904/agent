package com.helper.tempagent.template.export;

public interface ExportService {
    ExportResult export(String rendered, ExportFormat format);
}
