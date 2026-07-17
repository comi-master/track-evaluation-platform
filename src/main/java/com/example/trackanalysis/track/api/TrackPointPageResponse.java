package com.example.trackanalysis.track.api;

import java.util.List;

public record TrackPointPageResponse(
    long page, long size, long total, long pages, List<TrackPointResponse> items) {}
