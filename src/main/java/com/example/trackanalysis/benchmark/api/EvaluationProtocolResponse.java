package com.example.trackanalysis.benchmark.api;

public record EvaluationProtocolResponse(
    long id, String name, int versionNo, String description, String rulesJson) {}
