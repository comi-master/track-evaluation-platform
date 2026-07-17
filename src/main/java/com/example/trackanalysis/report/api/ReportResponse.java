package com.example.trackanalysis.report.api;

import com.example.trackanalysis.report.domain.ReportType;
import java.time.LocalDateTime;

public record ReportResponse(
    long reportId,
    long datasetId,
    String title,
    ReportType reportType,
    int sourceFileCount,
    LocalDateTime createdAt) {}
