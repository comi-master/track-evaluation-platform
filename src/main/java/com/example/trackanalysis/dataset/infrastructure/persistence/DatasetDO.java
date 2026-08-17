package com.example.trackanalysis.dataset.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;

@TableName("dataset")
public class DatasetDO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;
  private String name;
  private String description;

  @Version private Integer version;

  @TableLogic private Integer deleted;
  private String deleteStatus;
  private LocalDateTime deleteRequestedAt;
  private LocalDateTime deletedAt;
  private String deleteError;
  private Integer deleteAttemptCount;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
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

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public Integer getDeleted() {
    return deleted;
  }

  public void setDeleted(Integer deleted) {
    this.deleted = deleted;
  }

  public String getDeleteStatus() {
    return deleteStatus;
  }

  public void setDeleteStatus(String value) {
    deleteStatus = value;
  }

  public LocalDateTime getDeleteRequestedAt() {
    return deleteRequestedAt;
  }

  public void setDeleteRequestedAt(LocalDateTime value) {
    deleteRequestedAt = value;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime value) {
    deletedAt = value;
  }

  public String getDeleteError() {
    return deleteError;
  }

  public void setDeleteError(String value) {
    deleteError = value;
  }

  public Integer getDeleteAttemptCount() {
    return deleteAttemptCount;
  }

  public void setDeleteAttemptCount(Integer value) {
    deleteAttemptCount = value;
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
