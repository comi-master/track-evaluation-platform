package com.example.trackanalysis.messaging;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.trackanalysis.analysis.application.AnalysisApplicationService;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.common.metrics.AnalysisPerformanceMetrics;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.task.domain.AnalysisTaskStatus;
import com.example.trackanalysis.task.infrastructure.persistence.AnalysisTaskDO;
import com.example.trackanalysis.task.infrastructure.persistence.AnalysisTaskMapper;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import com.rabbitmq.client.Channel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AnalysisTaskConsumerTest {
  @Mock AnalysisTaskMapper tasks;
  @Mock AnalysisApplicationService analysis;
  @Mock AnalysisTaskPublisher publisher;
  @Mock TransactionTemplate tx;
  @Mock TrackFileMapper files;
  @Mock DatasetMapper datasets;
  @Mock TaskLeaseHeartbeat heartbeat;
  @Mock AnalysisPerformanceMetrics performance;
  @Mock Channel channel;
  AnalysisTaskConsumer consumer;
  Message raw;

  @BeforeEach
  void setUp() {
    consumer =
        new AnalysisTaskConsumer(
            tasks,
            analysis,
            publisher,
            tx,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
            files,
            datasets,
            new com.example.trackanalysis.task.application.AnalysisTaskProperties(
                3, 5000, 30000, 5000),
            heartbeat,
            performance);
    lenient()
        .when(heartbeat.start(anyLong(), anyString()))
        .thenReturn(
            new TaskLeaseHeartbeat.Lease(new AtomicBoolean(true), mock(ScheduledFuture.class)));
    MessageProperties properties = new MessageProperties();
    properties.setDeliveryTag(77);
    raw = new Message(new byte[0], properties);
    lenient()
        .doAnswer(i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null))
        .when(tx)
        .execute(any());
  }

  @Test
  void invalidMessagesAreRejectedWithoutRequeue() throws Exception {
    consumer.consume(new AnalysisTaskMessage(2, 1), raw, channel);
    verify(channel).basicNack(77, false, false);
    verifyNoInteractions(tasks, analysis);
  }

  @Test
  void databaseLoadFailureUsesBoundedInfrastructureRetry() throws Exception {
    when(tasks.selectById(1L)).thenThrow(new IllegalStateException("database unavailable"));
    consumer.consume(new AnalysisTaskMessage(1, 1), raw, channel);
    verify(publisher).publishInfrastructureRetry(1, 1);
    verify(channel).basicAck(77, false);
  }

  @Test
  void successAcksButRunningDuplicateIsDelayedForCrashRecovery() throws Exception {
    when(tasks.selectById(1L)).thenReturn(task(AnalysisTaskStatus.SUCCESS, 1, 3));
    consumer.consume(new AnalysisTaskMessage(1, 1), raw, channel);
    reset(channel);
    when(tasks.selectById(1L)).thenReturn(task(AnalysisTaskStatus.RUNNING, 1, 3));
    when(tasks.recoverExpiredRunning(eq(1L), any())).thenReturn(1);
    consumer.consume(new AnalysisTaskMessage(1, 1), raw, channel);
    verify(publisher).publishRetry(1);
    verify(tasks).recoverExpiredRunning(eq(1L), any());
    verify(channel).basicAck(77, false);
    verifyNoInteractions(analysis);
  }

  @Test
  void temporaryFailureSchedulesBoundedRetryAndAcksOriginal() throws Exception {
    AnalysisTaskDO pending = task(AnalysisTaskStatus.PENDING, 0, 3);
    AnalysisTaskDO running = task(AnalysisTaskStatus.RUNNING, 1, 3);
    when(tasks.selectById(1L)).thenReturn(pending, running, running);
    when(tasks.claim(eq(1L), anyString(), anyString(), any(), anyLong())).thenReturn(1);
    when(tasks.scheduleRetry(eq(1L), anyString(), anyString(), any())).thenReturn(1);
    doThrow(new IllegalStateException("database unavailable"))
        .when(analysis)
        .createForTask(eq(5L), eq(2d), any(LongConsumer.class));
    consumer.consume(new AnalysisTaskMessage(1, 1), raw, channel);
    verify(publisher).publishRetry(1);
    verify(channel).basicAck(77, false);
    verify(tasks, never()).markFailed(eq(1L), anyString(), any());
  }

  @Test
  void exhaustedAndPermanentFailuresGoToDeadQueue() throws Exception {
    AnalysisTaskDO pending = task(AnalysisTaskStatus.PENDING, 2, 3);
    AnalysisTaskDO running = task(AnalysisTaskStatus.RUNNING, 3, 3);
    when(tasks.selectById(1L)).thenReturn(pending, running, running);
    when(tasks.claim(eq(1L), anyString(), anyString(), any(), anyLong())).thenReturn(1);
    when(tasks.markFailedOwned(eq(1L), anyString(), anyString(), any())).thenReturn(1);
    doThrow(new IllegalStateException("database unavailable"))
        .when(analysis)
        .createForTask(eq(5L), eq(2d), any(LongConsumer.class));
    consumer.consume(new AnalysisTaskMessage(1, 1), raw, channel);
    verify(tasks).markFailedOwned(eq(1L), any(), eq("Temporary analysis failure"), any());
    verify(publisher).publishDead(1);
    verify(channel).basicAck(77, false);

    reset(channel, publisher, analysis, tasks);
    when(tasks.selectById(1L)).thenReturn(pending, running);
    when(tasks.claim(eq(1L), anyString(), anyString(), any(), anyLong())).thenReturn(1);
    when(tasks.markFailedOwned(eq(1L), anyString(), anyString(), any())).thenReturn(1);
    doThrow(new BusinessException(ErrorCode.CONFLICT, "sensitive point detail"))
        .when(analysis)
        .createForTask(eq(5L), eq(2d), any(LongConsumer.class));
    consumer.consume(new AnalysisTaskMessage(1, 1), raw, channel);
    verify(tasks).markFailedOwned(eq(1L), any(), eq("Analysis failed: CONFLICT"), any());
    verify(publisher).publishDead(1);
  }

  @Test
  void cacheMetadataFailureAfterSuccessOnlyAcks() throws Exception {
    AnalysisTaskDO pending = task(AnalysisTaskStatus.PENDING, 0, 3);
    AnalysisTaskDO running = task(AnalysisTaskStatus.RUNNING, 1, 3);
    when(tasks.selectById(1L)).thenReturn(pending, running);
    when(tasks.claim(eq(1L), anyString(), anyString(), any(), anyLong())).thenReturn(1);
    doThrow(new IllegalStateException("metadata unavailable")).when(files).selectById(5L);
    consumer.consume(new AnalysisTaskMessage(1, 1), raw, channel);
    verify(channel).basicAck(77, false);
    verify(publisher, never()).publishDead(anyLong());
    verify(publisher, never()).publishRetry(anyLong());
  }

  private AnalysisTaskDO task(AnalysisTaskStatus status, int attempts, int max) {
    AnalysisTaskDO task = new AnalysisTaskDO();
    task.setId(1L);
    task.setTrackFileId(5L);
    task.setAbnormalThreshold(2d);
    task.setStatus(status);
    task.setAttemptCount(attempts);
    task.setMaxAttempts(max);
    if (status == AnalysisTaskStatus.RUNNING) task.setLeaseToken("lease-token");
    return task;
  }
}
