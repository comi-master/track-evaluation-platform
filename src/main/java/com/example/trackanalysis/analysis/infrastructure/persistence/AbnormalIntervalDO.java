package com.example.trackanalysis.analysis.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("abnormal_interval")
public class AbnormalIntervalDO {
  private Long id, analysisResultId, startSequence, endSequence, pointCount;
  private Integer intervalNo;
  private Double startTime, endTime, peakError, peakErrorTime;
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public Long getAnalysisResultId() {
    return analysisResultId;
  }

  public void setAnalysisResultId(Long v) {
    analysisResultId = v;
  }

  public Integer getIntervalNo() {
    return intervalNo;
  }

  public void setIntervalNo(Integer v) {
    intervalNo = v;
  }

  public Long getStartSequence() {
    return startSequence;
  }

  public void setStartSequence(Long v) {
    startSequence = v;
  }

  public Long getEndSequence() {
    return endSequence;
  }

  public void setEndSequence(Long v) {
    endSequence = v;
  }

  public Long getPointCount() {
    return pointCount;
  }

  public void setPointCount(Long v) {
    pointCount = v;
  }

  public Double getStartTime() {
    return startTime;
  }

  public void setStartTime(Double v) {
    startTime = v;
  }

  public Double getEndTime() {
    return endTime;
  }

  public void setEndTime(Double v) {
    endTime = v;
  }

  public Double getPeakError() {
    return peakError;
  }

  public void setPeakError(Double v) {
    peakError = v;
  }

  public Double getPeakErrorTime() {
    return peakErrorTime;
  }

  public void setPeakErrorTime(Double v) {
    peakErrorTime = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }
}
