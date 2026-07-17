package com.example.trackanalysis.analysis.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CreateAnalysisRequest(@NotNull @DecimalMin("0.0") Double abnormalThreshold) {}
