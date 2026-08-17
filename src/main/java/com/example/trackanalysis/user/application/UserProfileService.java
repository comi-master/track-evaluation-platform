package com.example.trackanalysis.user.application;

import com.example.trackanalysis.audit.application.AuditApplicationService;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
  private final SysUserMapper mapper;
  private final PasswordEncoder encoder;
  private final AuditApplicationService audit;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  public UserProfileService(
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

  @Transactional
  public void updateProfile(long userId, String displayName, String email, String requestId, String ip) {
    if (displayName != null && displayName.length() > 100) {
      throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Display name must be at most 100 characters");
    }
    if (email != null && email.length() > 254) {
      throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Email must be at most 254 characters");
    }
    SysUserDO user = mapper.selectActiveByIdForUpdate(userId);
    if (user == null || !"ACTIVE".equals(user.getStatus())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "The account is no longer active");
    }
    user.setDisplayName(blankToNull(displayName));
    user.setEmail(blankToNull(email));
    user.setUpdatedAt(LocalDateTime.now(clock));
    if (mapper.updateById(user) != 1) {
      throw new BusinessException(ErrorCode.CONFLICT, "User was changed by another request");
    }
    audit.record(user.getId(), user.getUsername(), "PROFILE_UPDATE", "USER", String.valueOf(user.getId()), requestId, ip, null);
  }

  private String blankToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @Transactional
  public void changePassword(
      long userId,
      String currentPassword,
      String newPassword,
      String confirmation,
      String requestId,
      String ip) {
    if (newPassword == null || newPassword.length() < 12 || newPassword.length() > 128) {
      throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Password must be 12-128 characters");
    }
    if (!newPassword.equals(confirmation)) {
      throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "New passwords do not match");
    }
    SysUserDO user = mapper.selectActiveByIdForUpdate(userId);
    if (user == null || !"ACTIVE".equals(user.getStatus())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "The account is no longer active");
    }
    if (!encoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Current password is incorrect");
    }
    user.setPasswordHash(encoder.encode(newPassword));
    user.setAuthVersion(user.getAuthVersion() + 1);
    user.setUpdatedAt(LocalDateTime.now(clock));
    if (mapper.updateById(user) != 1) {
      throw new BusinessException(ErrorCode.CONFLICT, "User was changed by another request");
    }
    audit.record(
        user.getId(),
        user.getUsername(),
        "PASSWORD_CHANGE",
        "USER",
        String.valueOf(user.getId()),
        requestId,
        ip,
        null);
    events.publishEvent(new UserSecurityChangedEvent(user.getUsername()));
  }
}
