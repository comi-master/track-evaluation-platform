package com.example.trackanalysis.messaging;

import com.example.trackanalysis.analysis.application.AnalysisApplicationService;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.metrics.AnalysisPerformanceMetrics;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.task.application.AnalysisTaskProperties;
import com.example.trackanalysis.task.domain.AnalysisTaskStatus;
import com.example.trackanalysis.task.infrastructure.persistence.AnalysisTaskDO;
import com.example.trackanalysis.task.infrastructure.persistence.AnalysisTaskMapper;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AnalysisTaskConsumer {
  private static final Logger log = LoggerFactory.getLogger(AnalysisTaskConsumer.class);
  private final AnalysisTaskMapper tasks;
  private final AnalysisApplicationService analysis;
  private final AnalysisTaskPublisher publisher;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final TrackFileMapper files;
  private final DatasetMapper datasets;
  private final AnalysisTaskProperties properties;
  private final TaskLeaseHeartbeat heartbeat;
  private final AnalysisPerformanceMetrics performance;
  private final String workerId = UUID.randomUUID().toString();

  public AnalysisTaskConsumer(
      AnalysisTaskMapper tasks,
      AnalysisApplicationService analysis,
      AnalysisTaskPublisher publisher,
      TransactionTemplate tx,
      Clock clock,
      TrackFileMapper files,
      DatasetMapper datasets,
      AnalysisTaskProperties properties,
      TaskLeaseHeartbeat heartbeat,
      AnalysisPerformanceMetrics performance) {
    this.tasks = tasks;
    this.analysis = analysis;
    this.publisher = publisher;
    this.tx = tx;
    this.clock = clock;
    this.files = files;
    this.datasets = datasets;
    this.properties = properties;
    this.heartbeat = heartbeat;
    this.performance = performance;
  }

  @RabbitListener(queues = RabbitTopologyConfig.QUEUE, containerFactory = "manualAckFactory")
  public void consume(AnalysisTaskMessage command, Message raw, Channel channel)
      throws IOException {
    long tag = raw.getMessageProperties().getDeliveryTag();
    if (command == null || command.schemaVersion() != 1 || command.taskId() <= 0) {
      channel.basicNack(tag, false, false);
      return;
    }
    try {
      process(command, tag, channel);
    } catch (RuntimeException infrastructure) {
      handleInfrastructure(command.taskId(), raw, tag, channel, infrastructure);
    }
  }

  private void process(AnalysisTaskMessage command, long tag, Channel channel) throws IOException {
    AnalysisTaskDO task = tasks.selectById(command.taskId());
    if (task == null) {
      channel.basicAck(tag, false);
      return;
    }
    if (task.getStatus() == AnalysisTaskStatus.SUCCESS
        || task.getStatus() == AnalysisTaskStatus.CANCELLED) {
      channel.basicAck(tag, false);
      return;
    }
    if (task.getStatus() == AnalysisTaskStatus.RUNNING) {
      recoverOrDelayRunning(task, tag, channel);
      return;
    }
    if (task.getStatus() == AnalysisTaskStatus.FAILED) {
      deadOrReject(command.taskId(), tag, channel);
      return;
    }
    LocalDateTime claimedAt = LocalDateTime.now(clock);
    String leaseToken = UUID.randomUUID().toString();
    int claimed =
        tx.execute(
            s ->
                tasks.claim(
                    command.taskId(),
                    workerId,
                    leaseToken,
                    claimedAt,
                    properties.leaseDurationMilliseconds()));
    if (claimed != 1) {
      channel.basicAck(tag, false);
      return;
    }
    task = tasks.selectById(command.taskId());
    performance.recordBetween("task.queue.wait", task.getCreatedAt(), claimedAt);
    var executionTimer = performance.start();
    try (TaskLeaseHeartbeat.Lease lease = heartbeat.start(task.getId(), leaseToken)) {
      AnalysisTaskDO running = task;
      analysis.createForTask(
          running.getTrackFileId(),
          running.getAbnormalThreshold(),
          resultId -> {
            LocalDateTime now = LocalDateTime.now(clock);
            if (!lease.isOwned()
                || tasks.markSuccess(running.getId(), resultId, leaseToken, now) != 1)
              throw new IllegalStateException("Task state changed during completion");
          });
      try {
        var file = files.selectById(running.getTrackFileId());
        if (file != null) {
          var dataset = datasets.selectById(file.getDatasetId());
          if (dataset != null)
            analysis.evictAfterTask(dataset.getUserId(), file.getId(), file.getDatasetId());
          else log.warn("Analysis cache metadata unavailable for taskId={}", running.getId());
        } else log.warn("Analysis file metadata unavailable for taskId={}", running.getId());
      } catch (RuntimeException cacheMetadataFailure) {
        log.warn("Analysis cache invalidation metadata failed for taskId={}", running.getId());
        log.debug("Cache metadata detail", cacheMetadataFailure);
      }
      channel.basicAck(tag, false);
    } catch (BusinessException permanent) {
      if (tasks.markFailedOwned(
              task.getId(), leaseToken, safePermanent(permanent), LocalDateTime.now(clock))
          == 1) deadOrReject(task.getId(), tag, channel);
      else channel.basicAck(tag, false);
    } catch (RuntimeException temporary) {
      handleTemporary(task, tag, channel, temporary);
    } finally {
      performance.stop(executionTimer, "task.execution");
    }
  }

  private void recoverOrDelayRunning(AnalysisTaskDO task, long tag, Channel channel)
      throws IOException {
    LocalDateTime now = LocalDateTime.now(clock);
    if (task.getAttemptCount() >= task.getMaxAttempts()) {
      if (tasks.failExpiredExhausted(task.getId(), now) == 1)
        deadOrReject(task.getId(), tag, channel);
      else channel.basicAck(tag, false);
      return;
    }
    if (tasks.recoverExpiredRunning(task.getId(), now) == 1) publisher.publishRetry(task.getId());
    channel.basicAck(tag, false);
  }

  private void handleInfrastructure(
      long taskId, Message raw, long tag, Channel channel, RuntimeException failure)
      throws IOException {
    Object header = raw.getMessageProperties().getHeaders().get("x-infrastructure-attempt");
    int attempt = header instanceof Number number ? number.intValue() : 0;
    if (attempt < properties.maxAttempts()) {
      try {
        publisher.publishInfrastructureRetry(taskId, attempt + 1);
        channel.basicAck(tag, false);
        return;
      } catch (BusinessException publicationFailure) {
        log.warn("Infrastructure retry publication failed for taskId={}", taskId);
      }
    }
    deadOrReject(taskId, tag, channel);
    log.warn("Analysis infrastructure retry exhausted for taskId={}", taskId);
    log.debug("Infrastructure failure detail", failure);
  }

  private void handleTemporary(
      AnalysisTaskDO task, long tag, Channel channel, RuntimeException failure) throws IOException {
    String safe = "Temporary analysis failure";
    AnalysisTaskDO current = tasks.selectById(task.getId());
    if (current.getAttemptCount() < current.getMaxAttempts()
        && tasks.scheduleRetry(task.getId(), task.getLeaseToken(), safe, LocalDateTime.now(clock))
            == 1) {
      try {
        publisher.publishRetry(task.getId());
        channel.basicAck(tag, false);
        return;
      } catch (BusinessException publishFailure) {
        log.warn("Analysis retry publication failed for taskId={}", task.getId());
      }
    }
    if (tasks.markFailedOwned(task.getId(), task.getLeaseToken(), safe, LocalDateTime.now(clock))
        == 1) deadOrReject(task.getId(), tag, channel);
    else channel.basicAck(tag, false);
    log.debug("Temporary analysis failure detail", failure);
  }

  private void deadOrReject(long taskId, long tag, Channel channel) throws IOException {
    try {
      publisher.publishDead(taskId);
      channel.basicAck(tag, false);
    } catch (BusinessException failure) {
      channel.basicNack(tag, false, false);
    }
  }

  private String safePermanent(BusinessException failure) {
    return "Analysis failed: " + failure.errorCode().code();
  }
}
