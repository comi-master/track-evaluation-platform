package com.example.trackanalysis.benchmark.api;

import java.time.LocalDateTime;

public record AlgorithmProjectResponse(
    long id,
    String name,
    String description,
    String repositoryUrl,
    String visibility,
    String status,
    int version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
