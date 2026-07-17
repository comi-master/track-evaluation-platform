package com.example.trackanalysis.analysis.api;

import java.util.List;

public record ErrorSeriesResponse(
    long page, long size, long total, long pages, List<ErrorPointResponse> records) {}
