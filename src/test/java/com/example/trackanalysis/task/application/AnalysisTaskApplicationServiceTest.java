package com.example.trackanalysis.task.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.messaging.AnalysisTaskPublisher;
import com.example.trackanalysis.task.domain.AnalysisTaskStatus;
import com.example.trackanalysis.task.infrastructure.persistence.AnalysisTaskDO;
import com.example.trackanalysis.task.infrastructure.persistence.AnalysisTaskMapper;
import com.example.trackanalysis.track.domain.ParseStatus;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileDO;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AnalysisTaskApplicationServiceTest {
  @Mock AnalysisTaskMapper tasks;
  @Mock TrackFileMapper files;
  @Mock AnalysisTaskPublisher publisher;
  @Mock TransactionTemplate tx;
  AnalysisTaskApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new AnalysisTaskApplicationService(
            tasks,
            files,
            publisher,
            new AnalysisTaskProperties(3, 5000),
            tx,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    lenient()
        .doAnswer(
            i -> {
              ((java.util.function.Consumer<?>) i.getArgument(0)).accept(null);
              return null;
            })
        .when(tx)
        .executeWithoutResult(any());
  }

  @Test
  void rejectsNonFiniteNegativeUnownedAndUnparsedRequests() {
    assertThatThrownBy(() -> service.create(1, 2, Double.NaN))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.create(1, 2, -1)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.create(1, 2, 1)).isInstanceOf(BusinessException.class);
    TrackFileDO file = new TrackFileDO();
    file.setParseStatus(ParseStatus.FAILED);
    when(files.selectOwnedById(2, 1)).thenReturn(file);
    assertThatThrownBy(() -> service.create(1, 2, 1)).isInstanceOf(BusinessException.class);
  }

  @Test
  void createsPendingTaskThenPublishes() {
    TrackFileDO file = new TrackFileDO();
    file.setParseStatus(ParseStatus.PARSED);
    when(files.selectOwnedById(2, 1)).thenReturn(file);
    when(tasks.insertOwnedPending(any(), eq(1L)))
        .thenAnswer(
            i -> {
              ((AnalysisTaskDO) i.getArgument(0)).setId(9L);
              return 1;
            });
    AnalysisTaskDO saved = task(AnalysisTaskStatus.PENDING);
    when(tasks.selectOwnedById(9, 1)).thenReturn(saved);
    service.create(1, 2, 4);
    verify(publisher).publish(9);
  }

  @Test
  void publicationFailureMarksTaskFailed() {
    TrackFileDO file = new TrackFileDO();
    file.setParseStatus(ParseStatus.PARSED);
    when(files.selectOwnedById(2, 1)).thenReturn(file);
    when(tasks.insertOwnedPending(any(), eq(1L)))
        .thenAnswer(
            i -> {
              ((AnalysisTaskDO) i.getArgument(0)).setId(9L);
              return 1;
            });
    doThrow(
            new BusinessException(
                com.example.trackanalysis.common.exception.ErrorCode.INFRASTRUCTURE_ERROR, "safe"))
        .when(publisher)
        .publish(9);
    assertThatThrownBy(() -> service.create(1, 2, 4)).isInstanceOf(BusinessException.class);
    verify(tasks).markFailed(eq(9L), eq("Task publication failed"), any());
  }

  @Test
  void onlyOwnedFailedTaskCanBeRetried() {
    when(tasks.selectOwnedById(8, 1)).thenReturn(task(AnalysisTaskStatus.SUCCESS));
    assertThatThrownBy(() -> service.retry(1, 8)).isInstanceOf(BusinessException.class);
    when(tasks.resetFailedOwned(eq(8L), eq(1L), any())).thenReturn(1);
    when(tasks.selectOwnedById(8, 1)).thenReturn(task(AnalysisTaskStatus.PENDING));
    service.retry(1, 8);
    verify(publisher).publish(8);
  }

  private AnalysisTaskDO task(AnalysisTaskStatus status) {
    AnalysisTaskDO task = new AnalysisTaskDO();
    task.setId(status == AnalysisTaskStatus.PENDING ? 9L : 8L);
    task.setTrackFileId(2L);
    task.setAbnormalThreshold(4d);
    task.setStatus(status);
    task.setAttemptCount(0);
    task.setMaxAttempts(3);
    return task;
  }
}
