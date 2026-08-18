package com.example.trackanalysis.evaluation.api;

public record LeaderboardEntry(
    long projectId,
    String projectName,
    String algorithmVersion,
    String gitCommit,
    long submissionId,
    long evaluationRunId,
    String gateStatus,
    Double rmse,
    String createdAt) {}
