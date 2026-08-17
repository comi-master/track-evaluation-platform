package com.example.trackanalysis.benchmark.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateAlgorithmProjectVisibilityRequest(@NotBlank String visibility) {}
