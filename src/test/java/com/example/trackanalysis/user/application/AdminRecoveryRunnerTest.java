package com.example.trackanalysis.user.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.trackanalysis.user.infrastructure.persistence.SysUserDO;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

class AdminRecoveryRunnerTest {
  private final SysUserMapper users = mock(SysUserMapper.class);
  private final UserAdministrationService administration = mock(UserAdministrationService.class);
  private final ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

  @Test
  void disabledAdministratorCanBeRecovered() {
    var runner = runner(" recovery-admin ", "Strong-password-1");
    var user = user(7, "recovery-admin", "DISABLED");
    user.setDeleted(1);
    when(users.selectByUsernameIncludingDeleted("recovery-admin")).thenReturn(user);
    when(users.selectRoleCodes(7)).thenReturn(List.of("ADMIN"));

    runner.run(new DefaultApplicationArguments());

    verify(administration).validateRecoveryPassword("Strong-password-1");
    verify(administration)
        .restoreDeletedAdministrator(7, 7, "recovery-admin", "local-admin-recovery", "127.0.0.1");
    verify(administration)
        .setEnabled(7, true, 7, "recovery-admin", "local-admin-recovery", "127.0.0.1");
    verify(administration)
        .resetPassword(
            7, "Strong-password-1", 7, "recovery-admin", "local-admin-recovery", "127.0.0.1");
    verify(context).close();
  }

  @Test
  void activeAdministratorIsRejected() {
    var runner = runner("recovery-admin", "Strong-password-1");
    when(users.selectByUsernameIncludingDeleted("recovery-admin"))
        .thenReturn(user(7, "recovery-admin", "ACTIVE"));
    when(users.selectRoleCodes(7)).thenReturn(List.of("ADMIN"));
    when(users.countIncompleteLocalRecovery(7)).thenReturn(0);

    assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Recovery target is not DISABLED");
    verifyNoInteractions(administration);
  }

  @Test
  void incompleteLocalRecoveryCanFinishPasswordReset() {
    var runner = runner("recovery-admin", "Strong-password-1");
    var user = user(7, "recovery-admin", "ACTIVE");
    user.setDeleted(0);
    when(users.selectByUsernameIncludingDeleted("recovery-admin")).thenReturn(user);
    when(users.selectRoleCodes(7)).thenReturn(List.of("ADMIN"));
    when(users.countIncompleteLocalRecovery(7)).thenReturn(1);

    runner.run(new DefaultApplicationArguments());

    verify(administration).validateRecoveryPassword("Strong-password-1");
    verify(administration)
        .resetPassword(
            7, "Strong-password-1", 7, "recovery-admin", "local-admin-recovery", "127.0.0.1");
    verify(administration, never())
        .setEnabled(anyLong(), anyBoolean(), anyLong(), any(), any(), any());
    verify(administration, never())
        .restoreDeletedAdministrator(anyLong(), anyLong(), any(), any(), any());
    verify(context).close();
  }

  @Test
  void disabledNonAdministratorIsRejected() {
    var runner = runner("recovery-admin", "Strong-password-1");
    when(users.selectByUsernameIncludingDeleted("recovery-admin"))
        .thenReturn(user(7, "recovery-admin", "DISABLED"));
    when(users.selectRoleCodes(7)).thenReturn(List.of("RESEARCHER"));

    assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Recovery target is not ADMIN");
    verifyNoInteractions(administration);
  }

  @Test
  void missingUserIsRejected() {
    var runner = runner("missing-admin", "Strong-password-1");
    when(users.selectByUsernameIncludingDeleted("missing-admin")).thenReturn(null);

    assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Recovery target does not exist");
    verify(users, never()).selectRoleCodes(anyLong());
    verifyNoInteractions(administration);
  }

  private AdminRecoveryRunner runner(String username, String password) {
    var runner = new AdminRecoveryRunner(users, administration, context);
    Function<String, String> environment =
        name -> {
          if ("ADMIN_RECOVERY_USERNAME".equals(name)) return username;
          if ("ADMIN_RECOVERY_PASSWORD".equals(name)) return password;
          return null;
        };
    ReflectionTestUtils.setField(runner, "environment", environment);
    return runner;
  }

  private SysUserDO user(long id, String username, String status) {
    var user = new SysUserDO();
    user.setId(id);
    user.setUsername(username);
    user.setStatus(status);
    return user;
  }
}
