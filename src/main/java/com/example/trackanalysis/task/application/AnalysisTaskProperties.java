package com.example.trackanalysis.task.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "track.task")
public record AnalysisTaskProperties(
    int maxAttempts,
    long retryDelayMilliseconds,
    long leaseDurationMilliseconds,
    long heartbeatIntervalMilliseconds) {
  public AnalysisTaskProperties {
    if (maxAttempts < 1 || maxAttempts > 100)
      throw new IllegalArgumentException("Invalid maximum attempts");
    if (retryDelayMilliseconds < 100 || retryDelayMilliseconds > 3600000)
      throw new IllegalArgumentException("Invalid retry delay");
    if (leaseDurationMilliseconds < 1000 || leaseDurationMilliseconds > 86400000)
      throw new IllegalArgumentException("Invalid lease duration");
    if (heartbeatIntervalMilliseconds < 100
        || heartbeatIntervalMilliseconds * 2 >= leaseDurationMilliseconds)
      throw new IllegalArgumentException("Heartbeat interval must be well below lease duration");
  }
}
