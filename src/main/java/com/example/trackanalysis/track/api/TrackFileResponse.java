package com.example.trackanalysis.track.api;

import com.example.trackanalysis.track.domain.ParseStatus;
import com.example.trackanalysis.track.domain.TrackSource;
import java.time.LocalDateTime;

public record TrackFileResponse(
    long id,
    long datasetId,
    String originalName,
    String sha256,
    long fileSize,
    TrackSource trackSource,
    ParseStatus parseStatus,
    long pointCount,
    String parseError,
    LocalDateTime createdAt) {}
