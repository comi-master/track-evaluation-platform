package com.example.trackanalysis.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.audit.application.AuditApplicationService;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.user.domain.UserStatus;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {
  private static final Set<String> ROLES = Set.of("ADMIN", "RESEARCHER");
  private final SysUserMapper mapper;
  private final PasswordEncoder encoder;
  private final AuditApplicationService audit;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  public UserAdministrationService(
      SysUserMapper mapper,
      PasswordEncoder encoder,
      AuditApplicationService audit,
      Clock clock,
      ApplicationEventPublisher events) {
    this.mapper = mapper;
    this.encoder = encoder;
    this.audit = audit;
    this.clock = clock;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public Page<SysUserDO> list(int page, String keyword) {
    var q =
        new LambdaQueryWrapper<SysUserDO>()
            .eq(SysUserDO::getDeleted, 0)
            .and(
                keyword != null && !keyword.isBlank(),
                w ->
                    w.like(SysUserDO::getUsername, keyword.trim())
                        .or()
                        .like(SysUserDO::getDisplayName, keyword.trim()))
            .orderByDesc(SysUserDO::getCreatedAt);
    return mapper.selectPage(new Page<>(page, 20), q);
  }

  @Transactional(readOnly = true)
  public UserDetails details(long id) {
    SysUserDO user = requireUser(mapper.selectActiveById(id));
    return new UserDetails(user, mapper.selectRoleCodes(id));
  }

  @Transactional
  public void create(
      String username,
      String displayName,
      String email,
      String password,
      String role,
      long actorId,
      String actor,
      String requestId,
      String ip) {
    String normalized = UsernamePolicy.normalize(username);
    validatePassword(password);
    validateRole(role);
    SysUserDO user = new SysUserDO();
    user.setUsername(normalized);
    user.setDisplayName(blankToNull(displayName));
    user.setEmail(blankToNull(email));
    user.setPasswordHash(encoder.encode(password));
    user.setStatus(UserStatus.ACTIVE.name());
    try {
      mapper.insert(user);
      mapper.assignRole(user.getId(), role);
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.CONFLICT, "Username is already registered", e);
    }
    audit.record(
        actorId,
        actor,
        "USER_CREATE",
        "USER",
        String.valueOf(user.getId()),
        requestId,
        ip,
        "role=" + role);
  }

  @Transactional
  public void restoreDeletedAdministrator(
      long id, long actorId, String actor, String requestId, String ip) {
    mapper.lockRole("ADMIN");
    SysUserDO user = mapper.selectByIdIncludingDeletedForUpdate(id);
    if (user == null)
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User was not found");
    if (!mapper.selectRoleCodes(id).contains("ADMIN"))
      throw new BusinessException(ErrorCode.FORBIDDEN, "Recovery target is not ADMIN");
    if (!"DISABLED".equals(user.getStatus()) || !Integer.valueOf(1).equals(user.getDeleted()))
      throw new BusinessException(
          ErrorCode.CONFLICT, "Only a deleted disabled administrator can be restored");
    ensureUpdated(mapper.restoreDeleted(id, LocalDateTime.now(clock)));
    audit.record(actorId, actor, "USER_RESTORE", "USER", String.valueOf(id), requestId, ip, null);
    events.publishEvent(new UserSecurityChangedEvent(user.getUsername()));
  }

  public void validateRecoveryPassword(String password) {
    validatePassword(password);
  }

  @Transactional
  public void setEnabled(
      long id, boolean enabled, long actorId, String actor, String requestId, String ip) {
    mapper.lockRole("ADMIN");
    SysUserDO user = requireUser(mapper.selectActiveByIdForUpdate(id));
    if (!enabled
        && mapper.selectRoleCodes(id).contains("ADMIN")
        && mapper.countActiveByRole("ADMIN") <= 1)
      throw new BusinessException(
          ErrorCode.CONFLICT, "The only active administrator cannot be disabled");
    user.setStatus(enabled ? "ACTIVE" : "DISABLED");
    user.setAuthVersion(user.getAuthVersion() + 1);
    user.setUpdatedAt(LocalDateTime.now(clock));
    ensureUpdated(mapper.updateById(user));
    audit.record(
        actorId,
        actor,
        enabled ? "USER_ENABLE" : "USER_DISABLE",
        "USER",
        String.valueOf(id),
        requestId,
        ip,
        null);
    events.publishEvent(new UserSecurityChangedEvent(user.getUsername()));
  }

  @Transactional
  public void resetPassword(
      long id, String password, long actorId, String actor, String requestId, String ip) {
    validatePassword(password);
    SysUserDO user = requireUser(mapper.selectActiveByIdForUpdate(id));
    user.setPasswordHash(encoder.encode(password));
    user.setAuthVersion(user.getAuthVersion() + 1);
    user.setUpdatedAt(LocalDateTime.now(clock));
    ensureUpdated(mapper.updateById(user));
    audit.record(actorId, actor, "PASSWORD_RESET", "USER", String.valueOf(id), requestId, ip, null);
    events.publishEvent(new UserSecurityChangedEvent(user.getUsername()));
  }

  @Transactional
  public void update(
      long id,
      String displayName,
      String email,
      String role,
      long actorId,
      String actor,
      String requestId,
      String ip) {
    validateRole(role);
    mapper.lockRole("ADMIN");
    SysUserDO user = requireUser(mapper.selectActiveByIdForUpdate(id));
    var oldRoles = mapper.selectRoleCodes(id);
    if (oldRoles.contains("ADMIN")
        && !"ADMIN".equals(role)
        && "ACTIVE".equals(user.getStatus())
        && mapper.countActiveByRole("ADMIN") <= 1) {
      throw new BusinessException(
          ErrorCode.CONFLICT, "The only active administrator must retain the administrator role");
    }
    user.setDisplayName(blankToNull(displayName));
    user.setEmail(blankToNull(email));
    user.setAuthVersion(user.getAuthVersion() + 1);
    user.setUpdatedAt(LocalDateTime.now(clock));
    ensureUpdated(mapper.updateById(user));
    mapper.deleteRoles(id);
    mapper.assignRole(id, role);
    audit.record(
        actorId, actor, "USER_UPDATE", "USER", String.valueOf(id), requestId, ip, "role=" + role);
    events.publishEvent(new UserSecurityChangedEvent(user.getUsername()));
  }

  private SysUserDO requireUser(SysUserDO user) {
    if (user == null) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User was not found");
    }
    return user;
  }

  private void ensureUpdated(int changed) {
    if (changed != 1) {
      throw new BusinessException(ErrorCode.CONFLICT, "User was changed by another request");
    }
  }

  private void validatePassword(String value) {
    if (value == null || value.length() < 12 || value.length() > 128)
      throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Password must be 12-128 characters");
  }

  private void validateRole(String value) {
    if (!ROLES.contains(value))
      throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Role is invalid");
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record UserDetails(SysUserDO user, java.util.List<String> roles) {}
}
