package com.example.trackanalysis.task.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "track.task")
public record AnalysisTaskProperties(int maxAttempts, long retryDelayMilliseconds) {
  public AnalysisTaskProperties {
    if (maxAttempts < 1 || maxAttempts > 100)
      throw new IllegalArgumentException("Invalid maximum attempts");
    if (retryDelayMilliseconds < 100 || retryDelayMilliseconds > 3600000)
      throw new IllegalArgumentException("Invalid retry delay");
  }
}
