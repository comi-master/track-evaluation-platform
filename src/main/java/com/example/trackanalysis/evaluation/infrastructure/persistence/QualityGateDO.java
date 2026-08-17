package com.example.trackanalysis.evaluation.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("quality_gate")
public class QualityGateDO {
  @TableId(type = IdType.AUTO) private Long id;
  private Long evaluationRunId;
  private String metricCode;
  private Double actualValue;
  private Double thresholdValue;
  private String comparison;
  private Integer passed;
  private String detail;
  private LocalDateTime createdAt;
  public Long getId(){return id;} public void setId(Long v){id=v;}
  public Long getEvaluationRunId(){return evaluationRunId;} public void setEvaluationRunId(Long v){evaluationRunId=v;}
  public String getMetricCode(){return metricCode;} public void setMetricCode(String v){metricCode=v;}
  public Double getActualValue(){return actualValue;} public void setActualValue(Double v){actualValue=v;}
  public Double getThresholdValue(){return thresholdValue;} public void setThresholdValue(Double v){thresholdValue=v;}
  public String getComparison(){return comparison;} public void setComparison(String v){comparison=v;}
  public Integer getPassed(){return passed;} public void setPassed(Integer v){passed=v;}
  public String getDetail(){return detail;} public void setDetail(String v){detail=v;}
  public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
