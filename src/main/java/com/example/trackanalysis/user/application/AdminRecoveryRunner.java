package com.example.trackanalysis.user.application;

import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import java.util.List;
import java.util.function.Function;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "track.maintenance.admin-recovery.enabled", havingValue = "true")
public class AdminRecoveryRunner implements ApplicationRunner {
  private final SysUserMapper users;
  private final UserAdministrationService administration;
  private final ConfigurableApplicationContext context;
  private Function<String, String> environment = System::getenv;

  public AdminRecoveryRunner(
      SysUserMapper users,
      UserAdministrationService administration,
      ConfigurableApplicationContext context) {
    this.users = users;
    this.administration = administration;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    String username = required("ADMIN_RECOVERY_USERNAME").trim();
    String password = required("ADMIN_RECOVERY_PASSWORD");
    var user = users.selectByUsernameIncludingDeleted(username);
    if (user == null) throw new IllegalStateException("Recovery target does not exist");
    List<String> roles = users.selectRoleCodes(user.getId());
    if (!roles.contains("ADMIN")) throw new IllegalStateException("Recovery target is not ADMIN");
    boolean incompleteRecovery =
        "ACTIVE".equals(user.getStatus())
            && Integer.valueOf(0).equals(user.getDeleted())
            && users.countIncompleteLocalRecovery(user.getId()) == 1;
    if (!"DISABLED".equals(user.getStatus()) && !incompleteRecovery)
      throw new IllegalStateException("Recovery target is not DISABLED");

    String requestId = "local-admin-recovery";
    administration.validateRecoveryPassword(password);
    if (!incompleteRecovery) {
      if (Integer.valueOf(1).equals(user.getDeleted()))
        administration.restoreDeletedAdministrator(
            user.getId(), user.getId(), username, requestId, "127.0.0.1");
      administration.setEnabled(user.getId(), true, user.getId(), username, requestId, "127.0.0.1");
    }
    administration.resetPassword(
        user.getId(), password, user.getId(), username, requestId, "127.0.0.1");
    context.close();
  }

  private String required(String name) {
    String value = environment.apply(name);
    if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
    return value;
  }
}
