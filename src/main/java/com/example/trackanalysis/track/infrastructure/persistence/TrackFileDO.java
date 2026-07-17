package com.example.trackanalysis.track.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.trackanalysis.track.domain.ParseStatus;
import com.example.trackanalysis.track.domain.TrackSource;
import java.time.LocalDateTime;

@TableName("track_file")
public class TrackFileDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long datasetId;
  private String originalName;
  private String objectName;
  private String sha256;
  private Long fileSize;
  private TrackSource trackSource;
  private ParseStatus parseStatus;
  private Long pointCount;
  private String parseError;
  private Integer version;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(Long datasetId) {
    this.datasetId = datasetId;
  }

  public String getOriginalName() {
    return originalName;
  }

  public void setOriginalName(String originalName) {
    this.originalName = originalName;
  }

  public String getObjectName() {
    return objectName;
  }

  public void setObjectName(String objectName) {
    this.objectName = objectName;
  }

  public String getSha256() {
    return sha256;
  }

  public void setSha256(String sha256) {
    this.sha256 = sha256;
  }

  public Long getFileSize() {
    return fileSize;
  }

  public void setFileSize(Long fileSize) {
    this.fileSize = fileSize;
  }

  public TrackSource getTrackSource() {
    return trackSource;
  }

  public void setTrackSource(TrackSource trackSource) {
    this.trackSource = trackSource;
  }

  public ParseStatus getParseStatus() {
    return parseStatus;
  }

  public void setParseStatus(ParseStatus parseStatus) {
    this.parseStatus = parseStatus;
  }

  public Long getPointCount() {
    return pointCount;
  }

  public void setPointCount(Long pointCount) {
    this.pointCount = pointCount;
  }

  public String getParseError() {
    return parseError;
  }

  public void setParseError(String parseError) {
    this.parseError = parseError;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
