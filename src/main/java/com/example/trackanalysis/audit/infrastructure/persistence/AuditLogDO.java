package com.example.trackanalysis.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("audit_log")
public class AuditLogDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;
  private String usernameSnapshot;
  private String action;
  private String resourceType;
  private String resourceId;
  private String requestId;
  private String ipAddress;
  private String detail;
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long v) {
    userId = v;
  }

  public String getUsernameSnapshot() {
    return usernameSnapshot;
  }

  public void setUsernameSnapshot(String v) {
    usernameSnapshot = v;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String v) {
    action = v;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String v) {
    resourceType = v;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(String v) {
    resourceId = v;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String v) {
    requestId = v;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String v) {
    ipAddress = v;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String v) {
    detail = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }
}
