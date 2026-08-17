package com.example.trackanalysis.web.infrastructure;

import com.example.trackanalysis.track.domain.ParseStatus;
import com.example.trackanalysis.track.domain.TrackSource;
import java.time.LocalDateTime;

public class BusinessDatasetRow {
  private Long id;
  private Long ownerId;
  private String ownerUsername;
  private String name;
  private String description;
  private String deleteStatus;
  private Long fileId;
  private String originalName;
  private String sha256;
  private Long fileSize;
  private TrackSource trackSource;
  private ParseStatus parseStatus;
  private Long pointCount;
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(Long ownerId) {
    this.ownerId = ownerId;
  }

  public String getOwnerUsername() {
    return ownerUsername;
  }

  public void setOwnerUsername(String ownerUsername) {
    this.ownerUsername = ownerUsername;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDeleteStatus() {
    return deleteStatus;
  }

  public void setDeleteStatus(String value) {
    deleteStatus = value;
  }

  public Long getFileId() {
    return fileId;
  }

  public void setFileId(Long fileId) {
    this.fileId = fileId;
  }

  public String getOriginalName() {
    return originalName;
  }

  public void setOriginalName(String originalName) {
    this.originalName = originalName;
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

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
