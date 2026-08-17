package com.example.trackanalysis.benchmark.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("benchmark_version")
public class BenchmarkVersionDO {
  @TableId(type = IdType.AUTO) private Long id;
  private Long benchmarkId;
  private Integer versionNo;
  private Long referenceTrackFileId;
  private String formatVersion;
  private String description;
  private String status;
  private Long createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime publishedAt;

  public Long getId() { return id; }
  public void setId(Long value) { id = value; }
  public Long getBenchmarkId() { return benchmarkId; }
  public void setBenchmarkId(Long value) { benchmarkId = value; }
  public Integer getVersionNo() { return versionNo; }
  public void setVersionNo(Integer value) { versionNo = value; }
  public Long getReferenceTrackFileId() { return referenceTrackFileId; }
  public void setReferenceTrackFileId(Long value) { referenceTrackFileId = value; }
  public String getFormatVersion() { return formatVersion; }
  public void setFormatVersion(String value) { formatVersion = value; }
  public String getDescription() { return description; }
  public void setDescription(String value) { description = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
  public Long getCreatedBy() { return createdBy; }
  public void setCreatedBy(Long value) { createdBy = value; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime value) { createdAt = value; }
  public LocalDateTime getPublishedAt() { return publishedAt; }
  public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
}
