package com.example.trackanalysis.analysis.api;

import com.example.trackanalysis.track.domain.TrackSource;
import java.time.LocalDateTime;

public record DatasetAnalysisComparisonResponse(
    long fileId,
    String originalName,
    TrackSource trackSource,
    long analysisId,
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
    LocalDateTime analyzedAt) {}
