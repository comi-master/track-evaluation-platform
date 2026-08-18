package com.example.trackanalysis.evaluation.api;

public record CiGateResponse(
    long evaluationRunId,
    String status,
    String gateStatus,
    boolean releaseAllowed,
    String failureMessage) {}
