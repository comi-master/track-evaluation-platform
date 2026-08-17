package com.example.trackanalysis.task.application;

public record TaskAuditContext(Long actorId, String username, String requestId, String ipAddress) {
  public static TaskAuditContext fallback(long userId) {
    return new TaskAuditContext(userId, "user#" + userId, null, null);
  }
}
