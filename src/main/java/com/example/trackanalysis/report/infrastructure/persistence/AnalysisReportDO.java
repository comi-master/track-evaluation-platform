package com.example.trackanalysis.report.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.trackanalysis.report.domain.ReportType;
import java.time.LocalDateTime;

@TableName("analysis_report")
public class AnalysisReportDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long datasetId;
  private String title;
  private ReportType reportType;
  private Integer sourceFileCount;
  private String contentHtml;
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public Long getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(Long v) {
    datasetId = v;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public ReportType getReportType() {
    return reportType;
  }

  public void setReportType(ReportType v) {
    reportType = v;
  }

  public Integer getSourceFileCount() {
    return sourceFileCount;
  }

  public void setSourceFileCount(Integer v) {
    sourceFileCount = v;
  }

  public String getContentHtml() {
    return contentHtml;
  }

  public void setContentHtml(String v) {
    contentHtml = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }
}
