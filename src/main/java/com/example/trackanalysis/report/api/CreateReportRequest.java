package com.example.trackanalysis.report.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(@NotBlank @Size(max = 200) String title) {}
