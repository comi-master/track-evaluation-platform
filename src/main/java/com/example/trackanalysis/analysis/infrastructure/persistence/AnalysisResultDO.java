package com.example.trackanalysis.analysis.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("analysis_result")
public class AnalysisResultDO {
  private Long id, trackFileId, pointCount, abnormalCount;
  private Double abnormalThreshold,
      meanError,
      rmse,
      minError,
      maxError,
      standardDeviation,
      abnormalRatio,
      maxErrorTime;
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public Long getTrackFileId() {
    return trackFileId;
  }

  public void setTrackFileId(Long v) {
    trackFileId = v;
  }

  public Long getPointCount() {
    return pointCount;
  }

  public void setPointCount(Long v) {
    pointCount = v;
  }

  public Long getAbnormalCount() {
    return abnormalCount;
  }

  public void setAbnormalCount(Long v) {
    abnormalCount = v;
  }

  public Double getAbnormalThreshold() {
    return abnormalThreshold;
  }

  public void setAbnormalThreshold(Double v) {
    abnormalThreshold = v;
  }

  public Double getMeanError() {
    return meanError;
  }

  public void setMeanError(Double v) {
    meanError = v;
  }

  public Double getRmse() {
    return rmse;
  }

  public void setRmse(Double v) {
    rmse = v;
  }

  public Double getMinError() {
    return minError;
  }

  public void setMinError(Double v) {
    minError = v;
  }

  public Double getMaxError() {
    return maxError;
  }

  public void setMaxError(Double v) {
    maxError = v;
  }

  public Double getStandardDeviation() {
    return standardDeviation;
  }

  public void setStandardDeviation(Double v) {
    standardDeviation = v;
  }

  public Double getAbnormalRatio() {
    return abnormalRatio;
  }

  public void setAbnormalRatio(Double v) {
    abnormalRatio = v;
  }

  public Double getMaxErrorTime() {
    return maxErrorTime;
  }

  public void setMaxErrorTime(Double v) {
    maxErrorTime = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }
}
