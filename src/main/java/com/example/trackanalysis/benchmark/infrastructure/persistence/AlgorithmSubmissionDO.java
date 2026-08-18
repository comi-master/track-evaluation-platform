package com.example.trackanalysis.benchmark.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("algorithm_submission")
public class AlgorithmSubmissionDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private Long benchmarkVersionId;
  private Long protocolId;
  private Long outputTrackFileId;
  private String algorithmVersion;
  private String gitCommit;
  private String submissionKey;
  private String status;
  private String description;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long value) {
    projectId = value;
  }

  public Long getBenchmarkVersionId() {
    return benchmarkVersionId;
  }

  public void setBenchmarkVersionId(Long value) {
    benchmarkVersionId = value;
  }

  public Long getProtocolId() {
    return protocolId;
  }

  public void setProtocolId(Long value) {
    protocolId = value;
  }

  public Long getOutputTrackFileId() {
    return outputTrackFileId;
  }

  public void setOutputTrackFileId(Long value) {
    outputTrackFileId = value;
  }

  public String getAlgorithmVersion() {
    return algorithmVersion;
  }

  public void setAlgorithmVersion(String value) {
    algorithmVersion = value;
  }

  public String getGitCommit() {
    return gitCommit;
  }

  public void setGitCommit(String value) {
    gitCommit = value;
  }

  public String getSubmissionKey() {
    return submissionKey;
  }

  public void setSubmissionKey(String value) {
    submissionKey = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    description = value;
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
