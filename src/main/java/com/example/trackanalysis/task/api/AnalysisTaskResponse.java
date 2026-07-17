package com.example.trackanalysis.task.api;

import com.example.trackanalysis.task.domain.AnalysisTaskStatus;
import java.time.LocalDateTime;

public record AnalysisTaskResponse(
    long taskId,
    long fileId,
    double abnormalThreshold,
    AnalysisTaskStatus status,
    int attemptCount,
    int maxAttempts,
    Long analysisResultId,
    String safeErrorMessage,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
