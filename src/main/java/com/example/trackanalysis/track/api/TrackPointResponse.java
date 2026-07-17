package com.example.trackanalysis.track.api;

public record TrackPointResponse(
    long sequenceNo,
    double time,
    double trueX,
    double trueY,
    double trueZ,
    double trackX,
    double trackY,
    double trackZ) {}
