package com.example.trackanalysis.audit.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SafeAuditService {
  private static final Logger log = LoggerFactory.getLogger(SafeAuditService.class);
  private final AuditApplicationService audit;

  public SafeAuditService(AuditApplicationService audit) {
    this.audit = audit;
  }

  public void record(
      Long userId,
      String username,
      String action,
      String resourceType,
      String resourceId,
      String requestId,
      String ip,
      String detail) {
    try {
      audit.record(userId, username, action, resourceType, resourceId, requestId, ip, detail);
    } catch (RuntimeException exception) {
      log.error("Audit persistence failed for action {}", action, exception);
    }
  }
}
