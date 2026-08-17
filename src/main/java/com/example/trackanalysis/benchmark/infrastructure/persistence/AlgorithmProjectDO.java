package com.example.trackanalysis.benchmark.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("algorithm_project")
public class AlgorithmProjectDO {
  @TableId(type = IdType.AUTO) private Long id;
  private Long ownerUserId;
  private String name;
  private String description;
  private String repositoryUrl;
  private String visibility;
  private String status;
  private Integer version;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public Long getId() { return id; }
  public void setId(Long value) { id = value; }
  public Long getOwnerUserId() { return ownerUserId; }
  public void setOwnerUserId(Long value) { ownerUserId = value; }
  public String getName() { return name; }
  public void setName(String value) { name = value; }
  public String getDescription() { return description; }
  public void setDescription(String value) { description = value; }
  public String getRepositoryUrl() { return repositoryUrl; }
  public void setRepositoryUrl(String value) { repositoryUrl = value; }
  public String getVisibility() { return visibility; }
  public void setVisibility(String value) { visibility = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
  public Integer getVersion() { return version; }
  public void setVersion(Integer value) { version = value; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime value) { createdAt = value; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
