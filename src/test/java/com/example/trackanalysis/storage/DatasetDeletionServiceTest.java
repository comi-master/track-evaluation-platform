package com.example.trackanalysis.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.trackanalysis.audit.application.AuditApplicationService;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetDO;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.outbox.ReliableOutboxMapper;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileDO;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class DatasetDeletionServiceTest {
  DatasetMapper datasets = mock(DatasetMapper.class);
  TrackFileMapper files = mock(TrackFileMapper.class);
  ReliableOutboxMapper outbox = mock(ReliableOutboxMapper.class);
  AuditApplicationService audit = mock(AuditApplicationService.class);
  TransactionTemplate tx = mock(TransactionTemplate.class);
  DatasetDeletionService service;

  @BeforeEach
  void setup() {
    service =
        new DatasetDeletionService(
            datasets,
            files,
            outbox,
            audit,
            tx,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    doAnswer(
            i -> {
              ((java.util.function.Consumer<?>) i.getArgument(0)).accept(null);
              return null;
            })
        .when(tx)
        .executeWithoutResult(any());
    TrackFileDO file = new TrackFileDO();
    file.setObjectName("owner/object.csv");
    when(files.selectActiveDatasetFiles(9)).thenReturn(List.of(file));
    DatasetDO dataset = new DatasetDO();
    dataset.setUserId(7L);
    dataset.setDeleteStatus("ACTIVE");
    when(datasets.selectById(9L)).thenReturn(dataset);
  }

  @Test
  void requestPersistsStateEventAndAuditInOneTransaction() {
    when(datasets.requestDelete(eq(9L), eq(7L), any())).thenReturn(1);
    when(outbox.insert(anyString(), anyString(), anyString(), eq(9L), anyString(), any()))
        .thenReturn(1);
    service.request(7, 9, 7L, "researcher", "request", "127.0.0.1");
    verify(outbox)
        .insert(
            eq("dataset-delete:9"),
            eq("DATASET_OBJECT_DELETE"),
            eq("DATASET"),
            eq(9L),
            contains("owner/object.csv"),
            any());
    verify(audit)
        .record(
            eq(7L),
            eq("researcher"),
            eq("DATASET_DELETE_REQUEST"),
            eq("DATASET"),
            eq("9"),
            eq("request"),
            eq("127.0.0.1"),
            isNull());
  }

  @Test
  void eventFailureFailsTheWholeTransactionalCallback() {
    when(datasets.requestDelete(eq(9L), eq(7L), any())).thenReturn(1);
    assertThatThrownBy(() -> service.request(7, 9, 7L, "researcher", null, null))
        .isInstanceOf(IllegalStateException.class);
    verify(audit, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
  }
}
