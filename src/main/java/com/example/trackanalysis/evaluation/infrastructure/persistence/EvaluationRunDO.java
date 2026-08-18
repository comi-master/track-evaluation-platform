package com.example.trackanalysis.evaluation.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("evaluation_run")
public class EvaluationRunDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long submissionId;
  private Long analysisTaskId;
  private Long analysisResultId;
  private Long baselineRunId;
  private String status;
  private String gateStatus;
  private String metricsJson;
  private String failureMessage;
  private Integer version;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public Long getSubmissionId() {
    return submissionId;
  }

  public void setSubmissionId(Long v) {
    submissionId = v;
  }

  public Long getAnalysisTaskId() {
    return analysisTaskId;
  }

  public void setAnalysisTaskId(Long v) {
    analysisTaskId = v;
  }

  public Long getAnalysisResultId() {
    return analysisResultId;
  }

  public void setAnalysisResultId(Long v) {
    analysisResultId = v;
  }

  public Long getBaselineRunId() {
    return baselineRunId;
  }

  public void setBaselineRunId(Long v) {
    baselineRunId = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    status = v;
  }

  public String getGateStatus() {
    return gateStatus;
  }

  public void setGateStatus(String v) {
    gateStatus = v;
  }

  public String getMetricsJson() {
    return metricsJson;
  }

  public void setMetricsJson(String v) {
    metricsJson = v;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public void setFailureMessage(String v) {
    failureMessage = v;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer v) {
    version = v;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(LocalDateTime v) {
    startedAt = v;
  }

  public LocalDateTime getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(LocalDateTime v) {
    finishedAt = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime v) {
    updatedAt = v;
  }
}
