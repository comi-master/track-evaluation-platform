package com.example.trackanalysis.task.api;

import java.util.List;

public record AnalysisTaskPageResponse(
    long page, long size, long total, long pages, List<AnalysisTaskResponse> records) {}
