package com.example.trackanalysis.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.trackanalysis.auth.api.LoginRequest;
import com.example.trackanalysis.auth.api.RegisterRequest;
import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.auth.security.IssuedToken;
import com.example.trackanalysis.auth.security.JwtService;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

  @Mock private SysUserMapper userMapper;
  @Mock private JwtService jwtService;

  private AuthApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new AuthApplicationService(
            userMapper,
            new BCryptPasswordEncoder(),
            jwtService,
            Clock.fixed(Instant.parse("2026-07-16T08:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void registrationNormalizesUsernameAndStoresOnlyABcryptHash() {
    when(userMapper.insert(any(SysUserDO.class)))
        .thenAnswer(
            invocation -> {
              invocation.<SysUserDO>getArgument(0).setId(11L);
              return 1;
            });

    service.register(new RegisterRequest("  Researcher01 ", "plain-password"));

    ArgumentCaptor<SysUserDO> captor = ArgumentCaptor.forClass(SysUserDO.class);
    verify(userMapper).insert(captor.capture());
    SysUserDO user = captor.getValue();
    assertThat(user.getUsername()).isEqualTo("researcher01");
    assertThat(user.getPasswordHash()).startsWith("$2").doesNotContain("plain-password");
    assertThat(new BCryptPasswordEncoder().matches("plain-password", user.getPasswordHash()))
        .isTrue();
  }

  @Test
  void nonexistentUserAndWrongPasswordExposeTheSameError() {
    BusinessException missing =
        catchBusiness(() -> service.login(new LoginRequest("missing-user", "wrong-password")));
    SysUserDO user = activeUser("known-user", "correct-password");
    when(userMapper.selectActiveByUsername("known-user")).thenReturn(user);
    BusinessException wrong =
        catchBusiness(() -> service.login(new LoginRequest("known-user", "wrong-password")));

    assertThat(missing.errorCode()).isEqualTo(wrong.errorCode());
    assertThat(missing.getMessage()).isEqualTo(wrong.getMessage());
  }

  @Test
  void activeUserReceivesABearerToken() {
    SysUserDO user = activeUser("known-user", "correct-password");
    when(userMapper.selectActiveByUsername("known-user")).thenReturn(user);
    when(jwtService.issue(9L, "known-user", 2)).thenReturn(new IssuedToken("token-value", 7200));

    var response = service.login(new LoginRequest("KNOWN-USER", "correct-password"));

    assertThat(response.accessToken()).isEqualTo("token-value");
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.expiresIn()).isEqualTo(7200);
  }

  @Test
  void logoutUsesCurrentUserAndAuthVersionAsTheUpdatePredicate() {
    when(userMapper.incrementAuthVersion(
            any(Long.class), any(Integer.class), any(LocalDateTime.class)))
        .thenReturn(1);

    service.logout(new AuthenticatedUser(8L, "user008", 4));

    verify(userMapper)
        .incrementAuthVersion(
            org.mockito.ArgumentMatchers.eq(8L),
            org.mockito.ArgumentMatchers.eq(4),
            any(LocalDateTime.class));
  }

  private SysUserDO activeUser(String username, String password) {
    SysUserDO user = new SysUserDO();
    user.setId(9L);
    user.setUsername(username);
    user.setPasswordHash(new BCryptPasswordEncoder().encode(password));
    user.setStatus("ACTIVE");
    user.setAuthVersion(2);
    return user;
  }

  private BusinessException catchBusiness(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    try {
      call.call();
      throw new AssertionError("Expected BusinessException");
    } catch (BusinessException exception) {
      return exception;
    } catch (Throwable throwable) {
      throw new AssertionError(throwable);
    }
  }
}
