package com.example.trackanalysis.user.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.trackanalysis.audit.application.AuditApplicationService;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserProfileServiceTest {
  private final SysUserMapper mapper = mock(SysUserMapper.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final AuditApplicationService audit = mock(AuditApplicationService.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final UserProfileService service =
      new UserProfileService(
          mapper,
          encoder,
          audit,
          Clock.fixed(Instant.parse("2026-07-21T12:00:00Z"), ZoneOffset.UTC),
          events);
  private SysUserDO user;

  @BeforeEach
  void setUp() {
    user = new SysUserDO();
    user.setId(7L);
    user.setUsername("researcher");
    user.setPasswordHash("old-hash");
    user.setStatus("ACTIVE");
    user.setAuthVersion(3);
    user.setVersion(0);
    user.setDeleted(0);
    when(mapper.selectActiveByIdForUpdate(7L)).thenReturn(user);
  }

  @Test
  void rejectsIncorrectCurrentPasswordWithoutChangingAuthentication() {
    when(encoder.matches("wrong", "old-hash")).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.changePassword(
                    7L, "wrong", "new-password-123", "new-password-123", "r", "127.0.0.1"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Current password is incorrect");

    verify(mapper, never()).updateById(any(SysUserDO.class));
    verify(events, never()).publishEvent(any());
  }

  @Test
  void rejectsMismatchedConfirmationBeforeLoadingUser() {
    assertThatThrownBy(
            () ->
                service.changePassword(
                    7L, "old", "new-password-123", "different-1234", "r", "127.0.0.1"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("New passwords do not match");

    verify(mapper, never()).selectActiveByIdForUpdate(7L);
  }

  @Test
  void changesPasswordAndPublishesInvalidationEvent() {
    when(encoder.matches("old-password", "old-hash")).thenReturn(true);
    when(encoder.encode("new-password-123")).thenReturn("new-hash");
    when(mapper.updateById(user)).thenReturn(1);

    service.changePassword(
        7L, "old-password", "new-password-123", "new-password-123", "request", "127.0.0.1");

    verify(mapper).updateById(user);
    verify(audit)
        .record(7L, "researcher", "PASSWORD_CHANGE", "USER", "7", "request", "127.0.0.1", null);
    verify(events).publishEvent(new UserSecurityChangedEvent("researcher"));
  }
}
