package com.example.trackanalysis.track.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("track.file")
public record TrackFileProperties(long maxSizeBytes, long maxRows, int batchSize) {
  public TrackFileProperties {
    if (maxSizeBytes < 1 || maxRows < 1 || batchSize < 1) {
      throw new IllegalArgumentException("Track file limits must be positive");
    }
  }
}
