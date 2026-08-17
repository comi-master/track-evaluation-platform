package com.example.trackanalysis.storage;

import com.example.trackanalysis.audit.application.AuditApplicationService;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.outbox.ReliableOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("!no-persistence")
public class DatasetDeletionWorker {
  private static final Logger log = LoggerFactory.getLogger(DatasetDeletionWorker.class);
  private final ReliableOutboxMapper outbox;
  private final DatasetMapper datasets;
  private final ObjectStorageService storage;
  private final AuditApplicationService audit;
  private final TransactionTemplate tx;
  private final ObjectMapper json;
  private final Clock clock;

  public DatasetDeletionWorker(
      ReliableOutboxMapper outbox,
      DatasetMapper datasets,
      ObjectStorageService storage,
      AuditApplicationService audit,
      TransactionTemplate tx,
      ObjectMapper json,
      Clock clock) {
    this.outbox = outbox;
    this.datasets = datasets;
    this.storage = storage;
    this.audit = audit;
    this.tx = tx;
    this.json = json;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${track.cleanup.poll-milliseconds:2000}")
  public void poll() {
    LocalDateTime now = LocalDateTime.now(clock);
    outbox.recover(now.minusMinutes(5), now);
    var row = outbox.next("DATASET_OBJECT_DELETE", now);
    String token = UUID.randomUUID().toString();
    if (row == null || outbox.claim(row.getId(), token, now) != 1) return;
    try {
      datasets.resumeDelete(row.getAggregateId(), now);
      String objectName = json.readTree(row.getPayloadJson()).required("objectName").asText();
      storage.delete(objectName);
      tx.executeWithoutResult(
          s -> {
            int completed = datasets.completeDelete(row.getAggregateId(), LocalDateTime.now(clock));
            if (completed != 1) {
              String status = datasets.selectDeleteStatus(row.getAggregateId());
              if (!"DELETED".equals(status))
                throw new IllegalStateException("Dataset deletion state changed");
            }
            if (outbox.complete(row.getId(), token, LocalDateTime.now(clock)) != 1)
              throw new IllegalStateException("Dataset deletion claim was lost");
            audit.record(
                null,
                "system",
                "DATASET_DELETE_COMPLETE",
                "DATASET",
                String.valueOf(row.getAggregateId()),
                null,
                null,
                null);
          });
    } catch (Exception failure) {
      String safe = "Object cleanup temporarily failed";
      LocalDateTime failedAt = LocalDateTime.now(clock);
      tx.executeWithoutResult(
          s -> {
            int failed = datasets.failDelete(row.getAggregateId(), safe, failedAt);
            if (failed != 1) {
              String status = datasets.selectDeleteStatus(row.getAggregateId());
              if ("DELETED".equals(status)) {
                if (outbox.complete(row.getId(), token, failedAt) != 1)
                  throw new IllegalStateException("Dataset deletion claim was lost", failure);
                return;
              }
              throw new IllegalStateException("Dataset deletion state changed", failure);
            }
            long delay = Math.min(300L, 1L << Math.min(row.getAttemptCount() + 1, 8));
            int retried = outbox.retry(row.getId(), token, delay, safe);
            if (retried != 1)
              throw new IllegalStateException("Dataset deletion claim was lost", failure);
          });
      log.warn("Dataset object cleanup will be retried for datasetId={}", row.getAggregateId());
      log.debug("Dataset object cleanup detail", failure);
    }
  }
}
