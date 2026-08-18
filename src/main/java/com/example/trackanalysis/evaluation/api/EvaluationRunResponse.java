package com.example.trackanalysis.evaluation.api;

import java.time.LocalDateTime;
import java.util.List;

public record EvaluationRunResponse(
    long id,
    long submissionId,
    Long analysisTaskId,
    Long analysisResultId,
    String status,
    String gateStatus,
    String metricsJson,
    String failureMessage,
    List<QualityGateResponse> gates,
    LocalDateTime createdAt,
    LocalDateTime finishedAt) {}
