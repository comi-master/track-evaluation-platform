package com.example.trackanalysis.benchmark.api;
import jakarta.validation.constraints.*;
public record CreateEvaluationProtocolRequest(@NotBlank @Size(max=128) String name, @NotNull @Min(1) Integer versionNo, @NotBlank String rulesJson, @Size(max=500) String description) {}
