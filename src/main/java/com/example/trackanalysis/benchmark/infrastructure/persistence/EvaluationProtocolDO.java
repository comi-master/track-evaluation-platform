package com.example.trackanalysis.benchmark.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("evaluation_protocol")
public class EvaluationProtocolDO {
  @TableId(type = IdType.AUTO) private Long id;
  private String name;
  private Integer versionNo;
  private String description;
  private String rulesJson;
  private String visibility;
  private String status;
  private Long createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime publishedAt;

  public Long getId() { return id; }
  public void setId(Long value) { id = value; }
  public String getName() { return name; }
  public void setName(String value) { name = value; }
  public Integer getVersionNo() { return versionNo; }
  public void setVersionNo(Integer value) { versionNo = value; }
  public String getDescription() { return description; }
  public void setDescription(String value) { description = value; }
  public String getRulesJson() { return rulesJson; }
  public void setRulesJson(String value) { rulesJson = value; }
  public String getVisibility() { return visibility; }
  public void setVisibility(String value) { visibility = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
  public Long getCreatedBy() { return createdBy; }
  public void setCreatedBy(Long value) { createdBy = value; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime value) { createdAt = value; }
  public LocalDateTime getPublishedAt() { return publishedAt; }
  public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
}
