package com.example.trackanalysis.dataset.api;

import java.time.LocalDateTime;

public record DatasetResponse(
    long id,
    String name,
    String description,
    int version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
