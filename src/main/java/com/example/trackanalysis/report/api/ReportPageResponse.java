package com.example.trackanalysis.report.api;

import java.util.List;

public record ReportPageResponse(List<ReportResponse> items, long total, int page, int size) {}
