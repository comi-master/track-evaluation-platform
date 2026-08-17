package com.example.trackanalysis.benchmark.api;

import java.time.LocalDateTime;

public record AlgorithmSubmissionResponse(
    long id,
    long projectId,
    long benchmarkVersionId,
    long protocolId,
    long outputTrackFileId,
    String algorithmVersion,
    String gitCommit,
    String status,
    LocalDateTime createdAt) {}
