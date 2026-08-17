package com.example.trackanalysis.messaging;

import com.example.trackanalysis.task.application.AnalysisTaskProperties;
import com.example.trackanalysis.task.infrastructure.persistence.AnalysisTaskMapper;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** Shared bounded heartbeat scheduler; one lightweight scheduled action per running task. */
@Component
public class TaskLeaseHeartbeat {
  private final AnalysisTaskMapper tasks;
  private final AnalysisTaskProperties properties;
  private final Clock clock;
  private final ScheduledExecutorService scheduler =
      Executors.newScheduledThreadPool(
          Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors())),
          runnable -> {
            Thread thread = new Thread(runnable, "analysis-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
          });

  public TaskLeaseHeartbeat(
      AnalysisTaskMapper tasks, AnalysisTaskProperties properties, Clock clock) {
    this.tasks = tasks;
    this.properties = properties;
    this.clock = clock;
  }

  public Lease start(long taskId, String token) {
    AtomicBoolean owned = new AtomicBoolean(true);
    long interval = properties.heartbeatIntervalMilliseconds();
    ScheduledFuture<?> future =
        scheduler.scheduleAtFixedRate(
            () -> {
              try {
                LocalDateTime now = LocalDateTime.now(clock);
                if (tasks.renewLease(taskId, token, now, properties.leaseDurationMilliseconds())
                    != 1) owned.set(false);
              } catch (RuntimeException failure) {
                owned.set(false);
              }
            },
            interval,
            interval,
            TimeUnit.MILLISECONDS);
    return new Lease(owned, future);
  }

  @PreDestroy
  void close() {
    scheduler.shutdownNow();
  }

  public record Lease(AtomicBoolean owned, ScheduledFuture<?> future) implements AutoCloseable {
    public boolean isOwned() {
      return owned.get();
    }

    @Override
    public void close() {
      future.cancel(false);
    }
  }
}
