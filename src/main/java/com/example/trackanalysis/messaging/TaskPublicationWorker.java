package com.example.trackanalysis.messaging;

import com.example.trackanalysis.outbox.ReliableOutboxMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!no-persistence")
public class TaskPublicationWorker {
  private static final Logger log = LoggerFactory.getLogger(TaskPublicationWorker.class);
  private final ReliableOutboxMapper outbox;
  private final AnalysisTaskPublisher publisher;
  private final Clock clock;
  private final int batchSize;

  public TaskPublicationWorker(
      ReliableOutboxMapper outbox,
      AnalysisTaskPublisher publisher,
      Clock clock,
      @Value("${track.outbox.batch-size:100}") int batchSize) {
    this.outbox = outbox;
    this.publisher = publisher;
    this.clock = clock;
    this.batchSize = Math.max(1, batchSize);
  }

  @Scheduled(fixedDelayString = "${track.outbox.poll-milliseconds:1000}")
  public void poll() {
    LocalDateTime now = LocalDateTime.now(clock);
    outbox.recover(now.minusMinutes(5), now);
    for (int published = 0; published < batchSize; published++) {
      var row = outbox.next("TASK_PUBLISH", now);
      String token = UUID.randomUUID().toString();
      if (row == null || outbox.claim(row.getId(), token, now) != 1) return;
      try {
        publisher.publish(row.getAggregateId());
        if (outbox.complete(row.getId(), token, LocalDateTime.now(clock)) != 1)
          throw new IllegalStateException("Task publication claim was lost");
      } catch (RuntimeException failure) {
        String safe = "Task publication temporarily failed";
        if (outbox.retry(row.getId(), token, 5, safe) != 1)
          throw new IllegalStateException("Task publication claim was lost", failure);
        log.warn("Task publication will be retried for taskId={}", row.getAggregateId());
        log.debug("Task publication detail", failure);
      }
    }
  }
}
