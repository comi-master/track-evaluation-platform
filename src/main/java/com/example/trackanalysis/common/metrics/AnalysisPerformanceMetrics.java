package com.example.trackanalysis.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Low-cardinality timings for the asynchronous analysis pipeline. */
@Component
public class AnalysisPerformanceMetrics {
  private static final String TIMER_NAME = "track.analysis.stage";
  private final MeterRegistry registry;

  public AnalysisPerformanceMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public Timer.Sample start() {
    return Timer.start(registry);
  }

  public void stop(Timer.Sample sample, String stage) {
    Objects.requireNonNull(sample, "sample");
    sample.stop(timer(stage));
  }

  public void record(String stage, Duration duration) {
    if (duration != null && !duration.isNegative()) timer(stage).record(duration);
  }

  public void recordBetween(String stage, LocalDateTime start, LocalDateTime end) {
    if (start != null && end != null && !end.isBefore(start)) {
      record(stage, Duration.between(start, end));
    }
  }

  private Timer timer(String stage) {
    return registry.timer(TIMER_NAME, "stage", stage);
  }
}
