package com.example.trackanalysis.analysis.api;

public record AbnormalIntervalResponse(
    int intervalNo,
    long startSequence,
    long endSequence,
    double startTime,
    double endTime,
    long pointCount,
    double peakError,
    double peakErrorTime) {}
