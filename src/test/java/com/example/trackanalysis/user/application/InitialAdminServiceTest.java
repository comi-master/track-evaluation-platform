package com.example.trackanalysis.user.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.trackanalysis.auth.config.WebAuthProperties;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class InitialAdminServiceTest {

  @Test
  void createsAndAssignsAdministratorOnlyWhenNoneExists() {
    SysUserMapper mapper = org.mockito.Mockito.mock(SysUserMapper.class);
    PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    when(mapper.countActiveByRole("ADMIN")).thenReturn(0);
    when(encoder.encode("secure-password-123")).thenReturn("bcrypt-hash");
    when(mapper.insert(any(SysUserDO.class)))
        .thenAnswer(
            invocation -> {
              invocation.<SysUserDO>getArgument(0).setId(42L);
              return 1;
            });

    new InitialAdminService(
            mapper,
            encoder,
            new WebAuthProperties(false, "acceptance-admin", "secure-password-123"))
        .createIfNecessary();

    ArgumentCaptor<SysUserDO> inserted = ArgumentCaptor.forClass(SysUserDO.class);
    verify(mapper).insert(inserted.capture());
    verify(mapper).assignRole(42L, "ADMIN");
  }

  @Test
  void restartDoesNotCreateAnotherAdministrator() {
    SysUserMapper mapper = org.mockito.Mockito.mock(SysUserMapper.class);
    PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    when(mapper.countActiveByRole("ADMIN")).thenReturn(1);

    new InitialAdminService(
            mapper,
            encoder,
            new WebAuthProperties(false, "acceptance-admin", "secure-password-123"))
        .createIfNecessary();

    verify(mapper, never()).insert(any(SysUserDO.class));
    verify(mapper, never()).assignRole(any(Long.class), any(String.class));
  }
}
