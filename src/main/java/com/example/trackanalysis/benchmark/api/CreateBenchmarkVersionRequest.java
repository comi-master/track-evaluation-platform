package com.example.trackanalysis.benchmark.api;
import jakarta.validation.constraints.*;
public record CreateBenchmarkVersionRequest(@NotNull @Min(1) Long referenceTrackFileId, @NotNull @Min(1) Integer versionNo, @NotBlank @Size(max=32) String formatVersion, @Size(max=500) String description) {}
