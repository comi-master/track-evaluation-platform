package com.example.trackanalysis.task.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CreateAnalysisTaskRequest(@NotNull @DecimalMin("0.0") Double abnormalThreshold) {}
