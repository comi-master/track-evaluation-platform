package com.example.trackanalysis.analysis.api;

import java.util.List;

public record AnalysisPageResponse(
    long page, long size, long total, long pages, List<AnalysisResponse> records) {}
