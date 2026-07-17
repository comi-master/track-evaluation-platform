package com.example.trackanalysis.task.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.messaging.AnalysisTaskPublisher;
import com.example.trackanalysis.task.api.*;
import com.example.trackanalysis.task.domain.AnalysisTaskStatus;
import com.example.trackanalysis.task.infrastructure.persistence.*;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AnalysisTaskApplicationService {
  private final AnalysisTaskMapper tasks;
  private final TrackFileMapper files;
  private final AnalysisTaskPublisher publisher;
  private final AnalysisTaskProperties properties;
  private final TransactionTemplate tx;
  private final Clock clock;

  public AnalysisTaskApplicationService(
      AnalysisTaskMapper tasks,
      TrackFileMapper files,
      AnalysisTaskPublisher publisher,
      AnalysisTaskProperties properties,
      TransactionTemplate tx,
      Clock clock) {
    this.tasks = tasks;
    this.files = files;
    this.publisher = publisher;
    this.properties = properties;
    this.tx = tx;
    this.clock = clock;
  }

  public AnalysisTaskResponse create(long userId, long fileId, double threshold) {
    if (!Double.isFinite(threshold) || threshold < 0)
      throw new BusinessException(
          ErrorCode.INVALID_ARGUMENT, "Threshold must be finite and non-negative");
    var owned = files.selectOwnedById(fileId, userId);
    if (owned == null) throw notFound();
    if (!"PARSED".equals(owned.getParseStatus().name()))
      throw new BusinessException(ErrorCode.CONFLICT, "Track file must be parsed");
    LocalDateTime now = LocalDateTime.now(clock);
    AnalysisTaskDO task = new AnalysisTaskDO();
    task.setTrackFileId(fileId);
    task.setAbnormalThreshold(threshold);
    task.setStatus(AnalysisTaskStatus.PENDING);
    task.setAttemptCount(0);
    task.setMaxAttempts(properties.maxAttempts());
    task.setVersion(0);
    task.setCreatedAt(now);
    task.setUpdatedAt(now);
    tx.executeWithoutResult(
        s -> {
          if (tasks.insertOwnedPending(task, userId) != 1) throw notFound();
        });
    try {
      publisher.publish(task.getId());
    } catch (BusinessException exception) {
      tasks.markFailed(task.getId(), "Task publication failed", LocalDateTime.now(clock));
      throw exception;
    }
    return get(userId, task.getId());
  }

  public AnalysisTaskResponse get(long userId, long taskId) {
    var task = tasks.selectOwnedById(taskId, userId);
    if (task == null) throw notFound();
    return response(task);
  }

  public AnalysisTaskPageResponse history(
      long userId, long fileId, int page, int size, AnalysisTaskStatus status) {
    if (files.selectOwnedById(fileId, userId) == null) throw notFound();
    var result = tasks.selectOwnedPage(new Page<>(page, size), fileId, userId, status);
    return new AnalysisTaskPageResponse(
        result.getCurrent(),
        result.getSize(),
        result.getTotal(),
        result.getPages(),
        result.getRecords().stream().map(this::response).toList());
  }

  public AnalysisTaskResponse retry(long userId, long taskId) {
    if (tasks.selectOwnedById(taskId, userId) == null) throw notFound();
    if (tasks.resetFailedOwned(taskId, userId, LocalDateTime.now(clock)) != 1)
      throw new BusinessException(ErrorCode.CONFLICT, "Only failed tasks can be retried");
    try {
      publisher.publish(taskId);
    } catch (BusinessException exception) {
      tasks.markFailed(taskId, "Task publication failed", LocalDateTime.now(clock));
      throw exception;
    }
    return get(userId, taskId);
  }

  private AnalysisTaskResponse response(AnalysisTaskDO t) {
    return new AnalysisTaskResponse(
        t.getId(),
        t.getTrackFileId(),
        t.getAbnormalThreshold(),
        t.getStatus(),
        t.getAttemptCount(),
        t.getMaxAttempts(),
        t.getAnalysisResultId(),
        t.getErrorMessage(),
        t.getStartedAt(),
        t.getFinishedAt(),
        t.getCreatedAt(),
        t.getUpdatedAt());
  }

  private BusinessException notFound() {
    return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Analysis task was not found");
  }
}
