package com.example.trackanalysis.analysis.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "track.cache")
public record AnalysisCacheProperties(Duration latestTtl, Duration comparisonTtl) {
  public AnalysisCacheProperties {
    if (latestTtl.isNegative()
        || latestTtl.isZero()
        || comparisonTtl.isNegative()
        || comparisonTtl.isZero()) throw new IllegalArgumentException("Cache TTL must be positive");
  }
}
