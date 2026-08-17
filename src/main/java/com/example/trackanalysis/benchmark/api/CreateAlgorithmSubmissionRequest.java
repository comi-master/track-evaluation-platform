package com.example.trackanalysis.benchmark.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAlgorithmSubmissionRequest(
    @NotNull @Min(1) Long projectId,
    @NotNull @Min(1) Long benchmarkVersionId,
    @NotNull @Min(1) Long protocolId,
    @NotNull @Min(1) Long outputTrackFileId,
    @NotBlank @Size(max = 128) String algorithmVersion,
    @Size(max = 128) String gitCommit,
    @Size(max = 500) String description) {}
