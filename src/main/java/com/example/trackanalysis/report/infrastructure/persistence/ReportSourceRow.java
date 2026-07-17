package com.example.trackanalysis.report.infrastructure.persistence;

import java.time.LocalDateTime;

public record ReportSourceRow(
    long fileId,
    String originalName,
    String trackSource,
    long pointCount,
    double abnormalThreshold,
    double meanError,
    double rmse,
    double minError,
    double maxError,
    double standardDeviation,
    long abnormalCount,
    double abnormalRatio,
    double maxErrorTime,
    LocalDateTime analyzedAt) {}
