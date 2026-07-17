package com.example.trackanalysis.track.infrastructure.persistence;

import java.time.LocalDateTime;

public class TrackPointDO {
  private Long id;
  private Long trackFileId;
  private Long sequenceNo;
  private Double timeValue;
  private Double trueX;
  private Double trueY;
  private Double trueZ;
  private Double trackX;
  private Double trackY;
  private Double trackZ;
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getTrackFileId() {
    return trackFileId;
  }

  public void setTrackFileId(Long trackFileId) {
    this.trackFileId = trackFileId;
  }

  public Long getSequenceNo() {
    return sequenceNo;
  }

  public void setSequenceNo(Long sequenceNo) {
    this.sequenceNo = sequenceNo;
  }

  public Double getTimeValue() {
    return timeValue;
  }

  public void setTimeValue(Double timeValue) {
    this.timeValue = timeValue;
  }

  public Double getTrueX() {
    return trueX;
  }

  public void setTrueX(Double trueX) {
    this.trueX = trueX;
  }

  public Double getTrueY() {
    return trueY;
  }

  public void setTrueY(Double trueY) {
    this.trueY = trueY;
  }

  public Double getTrueZ() {
    return trueZ;
  }

  public void setTrueZ(Double trueZ) {
    this.trueZ = trueZ;
  }

  public Double getTrackX() {
    return trackX;
  }

  public void setTrackX(Double trackX) {
    this.trackX = trackX;
  }

  public Double getTrackY() {
    return trackY;
  }

  public void setTrackY(Double trackY) {
    this.trackY = trackY;
  }

  public Double getTrackZ() {
    return trackZ;
  }

  public void setTrackZ(Double trackZ) {
    this.trackZ = trackZ;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
