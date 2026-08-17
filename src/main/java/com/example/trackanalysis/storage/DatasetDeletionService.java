package com.example.trackanalysis.storage;

import com.example.trackanalysis.audit.application.AuditApplicationService;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.outbox.ReliableOutboxMapper;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DatasetDeletionService {
  private final DatasetMapper datasets;
  private final TrackFileMapper files;
  private final ReliableOutboxMapper outbox;
  private final AuditApplicationService audit;
  private final TransactionTemplate tx;
  private final ObjectMapper json;
  private final Clock clock;

  public DatasetDeletionService(
      DatasetMapper datasets,
      TrackFileMapper files,
      ReliableOutboxMapper outbox,
      AuditApplicationService audit,
      TransactionTemplate tx,
      ObjectMapper json,
      Clock clock) {
    this.datasets = datasets;
    this.files = files;
    this.outbox = outbox;
    this.audit = audit;
    this.tx = tx;
    this.json = json;
    this.clock = clock;
  }

  public void request(
      long ownerId, long datasetId, Long actorId, String actor, String requestId, String ip) {
    var current = datasets.selectById(datasetId);
    if (current == null || !Long.valueOf(ownerId).equals(current.getUserId()))
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Dataset was not found");
    if ("DELETE_PENDING".equals(current.getDeleteStatus())
        || "DELETED".equals(current.getDeleteStatus())) return;
    LocalDateTime now = LocalDateTime.now(clock);
    tx.executeWithoutResult(
        s -> {
          int changed = datasets.requestDelete(datasetId, ownerId, now);
          if (changed == 0) {
            var existing = datasets.selectById(datasetId);
            if (existing == null)
              throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Dataset was not found");
            return;
          }
          if (files.countNonTerminalTasks(datasetId) > 0)
            throw new BusinessException(
                ErrorCode.CONFLICT, "Dataset has a pending or running task");
          var stored = files.selectActiveDatasetFiles(datasetId);
          if (stored.size() > 1)
            throw new BusinessException(ErrorCode.CONFLICT, "Dataset has multiple stored files");
          if (stored.isEmpty()) {
            if (datasets.completeDelete(datasetId, now) != 1)
              throw new IllegalStateException("Empty dataset deletion state changed");
            audit.record(
                actorId,
                actor,
                "DATASET_DELETE_COMPLETE",
                "DATASET",
                String.valueOf(datasetId),
                requestId,
                ip,
                "no stored object");
            return;
          }
          String payload;
          try {
            payload = json.writeValueAsString(Map.of("objectName", stored.get(0).getObjectName()));
          } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
          }
          if (outbox.insert(
                  "dataset-delete:" + datasetId,
                  "DATASET_OBJECT_DELETE",
                  "DATASET",
                  datasetId,
                  payload,
                  now)
              != 1) throw new IllegalStateException("Dataset cleanup event was not created");
          audit.record(
              actorId,
              actor,
              "DATASET_DELETE_REQUEST",
              "DATASET",
              String.valueOf(datasetId),
              requestId,
              ip,
              null);
        });
  }
}
