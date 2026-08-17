package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.user.application.UserAdministrationService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdministratorConcurrencyIT extends MySqlIntegrationTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired UserAdministrationService users;

  @Test
  void simultaneousCrossDisableNeverRemovesEveryAdministrator() throws Exception {
    long a = insertAdmin("race-admin-a");
    long b = insertAdmin("race-admin-b");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    List<Throwable> failures = new ArrayList<>();
    var executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          executor.submit(() -> disableAfterBarrier(b, a, "race-admin-a", ready, start, failures));
      Future<?> second =
          executor.submit(() -> disableAfterBarrier(a, b, "race-admin-b", ready, start, failures));
      ready.await();
      start.countDown();
      first.get();
      second.get();
    } finally {
      executor.shutdownNow();
    }

    Integer active =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id
            JOIN sys_role r ON r.id=ur.role_id WHERE r.code='ADMIN' AND u.status='ACTIVE' AND u.deleted=0
            """,
            Integer.class);
    assertThat(active).isEqualTo(1);
    assertThat(failures).hasSize(1).first().isInstanceOf(BusinessException.class);
    Integer audits =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE action='USER_DISABLE' AND resource_id IN (?,?)",
            Integer.class,
            String.valueOf(a),
            String.valueOf(b));
    assertThat(audits).isEqualTo(1);
  }

  private void disableAfterBarrier(
      long target,
      long actorId,
      String actor,
      CountDownLatch ready,
      CountDownLatch start,
      List<Throwable> failures) {
    ready.countDown();
    try {
      start.await();
      users.setEnabled(target, false, actorId, actor, "concurrency-it", "127.0.0.1");
    } catch (Throwable failure) {
      synchronized (failures) {
        failures.add(failure);
      }
    }
  }

  private long insertAdmin(String username) {
    jdbc.update(
        """
        INSERT INTO sys_user(username,password_hash,status,auth_version,version,deleted,created_at,updated_at)
        VALUES (?, '$2a$10$012345678901234567890u123456789012345678901234567890123', 'ACTIVE',0,0,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """,
        username);
    Long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
    jdbc.update(
        "INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code='ADMIN'",
        id);
    return id;
  }
}
