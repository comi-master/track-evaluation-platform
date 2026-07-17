package com.example.trackanalysis.track.api;

import java.util.List;

public record TrackFilePageResponse(
    long page, long size, long total, long pages, List<TrackFileResponse> items) {}
