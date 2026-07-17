package com.example.trackanalysis.analysis.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "track.analysis")
public record AnalysisProperties(int batchSize) {
  public AnalysisProperties {
    if (batchSize < 1 || batchSize > 10000)
      throw new IllegalArgumentException("Invalid analysis batch size");
  }
}
