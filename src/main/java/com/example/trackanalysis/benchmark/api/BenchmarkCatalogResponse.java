package com.example.trackanalysis.benchmark.api;

import java.util.List;

public record BenchmarkCatalogResponse(
    long id, String name, String description, List<BenchmarkVersionResponse> versions) {}
