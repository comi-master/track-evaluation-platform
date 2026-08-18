package com.example.trackanalysis.evaluation.api;

public record QualityGateResponse(
    String metricCode,
    Double actualValue,
    Double thresholdValue,
    String comparison,
    boolean passed,
    String detail) {}
