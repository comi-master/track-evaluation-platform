package com.example.trackanalysis.task.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.trackanalysis.task.domain.AnalysisTaskStatus;
import java.time.LocalDateTime;

@TableName("analysis_task")
public class AnalysisTaskDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long trackFileId;
  private Double abnormalThreshold;
  private AnalysisTaskStatus status;
  private Integer attemptCount;
  private Integer maxAttempts;
  private Long analysisResultId;
  private String errorMessage;
  private Integer version;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }

  public Long getTrackFileId() {
    return trackFileId;
  }

  public void setTrackFileId(Long value) {
    trackFileId = value;
  }

  public Double getAbnormalThreshold() {
    return abnormalThreshold;
  }

  public void setAbnormalThreshold(Double value) {
    abnormalThreshold = value;
  }

  public AnalysisTaskStatus getStatus() {
    return status;
  }

  public void setStatus(AnalysisTaskStatus value) {
    status = value;
  }

  public Integer getAttemptCount() {
    return attemptCount;
  }

  public void setAttemptCount(Integer value) {
    attemptCount = value;
  }

  public Integer getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(Integer value) {
    maxAttempts = value;
  }

  public Long getAnalysisResultId() {
    return analysisResultId;
  }

  public void setAnalysisResultId(Long value) {
    analysisResultId = value;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String value) {
    errorMessage = value;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer value) {
    version = value;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(LocalDateTime value) {
    startedAt = value;
  }

  public LocalDateTime getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(LocalDateTime value) {
    finishedAt = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime value) {
    updatedAt = value;
  }
}
