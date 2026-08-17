package com.example.trackanalysis.audit.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.audit.infrastructure.persistence.AuditLogDO;
import com.example.trackanalysis.audit.infrastructure.persistence.AuditLogMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditApplicationService {
  private final AuditLogMapper mapper;
  private final Clock clock;

  public AuditApplicationService(AuditLogMapper mapper, Clock clock) {
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional
  public void record(
      Long userId,
      String username,
      String action,
      String resourceType,
      String resourceId,
      String requestId,
      String ip,
      String detail) {
    AuditLogDO row = new AuditLogDO();
    row.setUserId(userId);
    row.setUsernameSnapshot(username);
    row.setAction(action);
    row.setResourceType(resourceType);
    row.setResourceId(resourceId);
    row.setRequestId(requestId);
    row.setIpAddress(ip);
    row.setDetail(detail);
    row.setCreatedAt(LocalDateTime.now(clock));
    mapper.insert(row);
  }

  @Transactional(readOnly = true)
  public Page<AuditLogDO> list(int page, int size, String username, String action) {
    var query =
        new LambdaQueryWrapper<AuditLogDO>()
            .like(
                username != null && !username.isBlank(),
                AuditLogDO::getUsernameSnapshot,
                username == null ? null : username.trim())
            .eq(action != null && !action.isBlank(), AuditLogDO::getAction, action)
            .orderByDesc(AuditLogDO::getCreatedAt)
            .orderByDesc(AuditLogDO::getId);
    return mapper.selectPage(new Page<>(page, Math.min(size, 100)), query);
  }
}
