package com.example.trackanalysis.analysis.api;

import java.time.LocalDateTime;
import java.util.List;

public record AnalysisResponse(
    long id,
    long trackFileId,
    double abnormalThreshold,
    long pointCount,
    double meanError,
    double rmse,
    double minError,
    double maxError,
    double standardDeviation,
    long abnormalCount,
    double abnormalRatio,
    double maxErrorTime,
    LocalDateTime createdAt,
    List<AbnormalIntervalResponse> intervals) {}
