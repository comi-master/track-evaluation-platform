package com.example.trackanalysis.track.application;

import java.io.InputStream;

public record TrackFileDownload(String fileName, long size, InputStream content) {}
