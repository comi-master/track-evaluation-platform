package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.outbox.ReliableOutboxMapper;
import com.example.trackanalysis.task.infrastructure.persistence.AnalysisTaskMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TaskLeaseMapperIT extends MySqlIntegrationTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired AnalysisTaskMapper tasks;
  @Autowired ReliableOutboxMapper outbox;
  @Autowired DatasetMapper datasets;
  long taskId;
  long datasetId;

  @BeforeEach
  void data() {
    long user = 900001, dataset = 900002, file = 900003;
    datasetId = dataset;
    jdbc.update(
        "INSERT INTO sys_user(id,username,password_hash,status) VALUES(?,?,?,'ACTIVE')",
        user,
        "lease_" + UUID.randomUUID().toString().replace("-", ""),
        "hash");
    jdbc.update("INSERT INTO dataset(id,user_id,name) VALUES(?,?,?)", dataset, user, "lease");
    jdbc.update(
        """
        INSERT INTO track_file(id,dataset_id,original_name,object_name,sha256,file_size,track_source,parse_status)
        VALUES(?,?,?,?,?,1,'RADAR','PARSED')
        """,
        file,
        dataset,
        "lease.csv",
        "lease/" + UUID.randomUUID(),
        "a".repeat(64));
    jdbc.update(
        """
        INSERT INTO analysis_task(track_file_id,abnormal_threshold,status,attempt_count,max_attempts,version)
        VALUES(?,1,'PENDING',0,3,0)
        """,
        file);
    taskId = jdbc.queryForObject("SELECT MAX(id) FROM analysis_task", Long.class);
  }

  @Test
  void claimAndHeartbeatRequireCurrentToken() {
    LocalDateTime now = LocalDateTime.parse("2026-01-01T00:00:00");
    assertThat(tasks.claim(taskId, "worker-a", "token-a", now, 30000)).isOne();
    assertThat(tasks.claim(taskId, "worker-b", "token-b", now, 30000)).isZero();
    assertThat(tasks.renewLease(taskId, "wrong", now.plusSeconds(5), 30000)).isZero();
    assertThat(tasks.renewLease(taskId, "token-a", now.plusSeconds(5), 30000)).isOne();
  }

  @Test
  void activeLeaseCannotBeRecoveredOrFailedNearMaximum() {
    LocalDateTime now = LocalDateTime.parse("2026-01-01T00:00:00");
    tasks.claim(taskId, "worker", "token", now, 30000);
    jdbc.update("UPDATE analysis_task SET attempt_count=max_attempts WHERE id=?", taskId);
    assertThat(tasks.recoverExpiredRunning(taskId, now.plusSeconds(10))).isZero();
    assertThat(tasks.failExpiredExhausted(taskId, now.plusSeconds(10))).isZero();
  }

  @Test
  void expiredLeaseIsRecoveredOnceAndOldWorkerCannotComplete() {
    LocalDateTime now = LocalDateTime.parse("2026-01-01T00:00:00");
    tasks.claim(taskId, "old", "old-token", now, 30000);
    jdbc.update(
        "UPDATE analysis_task SET lease_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",
        taskId);
    assertThat(tasks.recoverExpiredRunning(taskId, now.plusSeconds(31))).isOne();
    assertThat(tasks.recoverExpiredRunning(taskId, now.plusSeconds(31))).isZero();
    assertThat(tasks.markSuccess(taskId, 77, "old-token", now.plusSeconds(32))).isZero();
  }

  @Test
  void newOwnerCannotBeOverwrittenByOldOwner() {
    LocalDateTime now = LocalDateTime.parse("2026-01-01T00:00:00");
    tasks.claim(taskId, "old", "old-token", now, 1000);
    jdbc.update(
        "UPDATE analysis_task SET lease_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",
        taskId);
    tasks.recoverExpiredRunning(taskId, now.plusSeconds(2));
    tasks.claim(taskId, "new", "new-token", now.plusSeconds(2), 30000);
    assertThat(tasks.markSuccess(taskId, 77, "old-token", now.plusSeconds(3))).isZero();
  }

  @Test
  void expiredExhaustedLeaseFailsExactlyOnce() {
    LocalDateTime now = LocalDateTime.parse("2026-01-01T00:00:00");
    tasks.claim(taskId, "worker", "token", now, 1000);
    jdbc.update(
        "UPDATE analysis_task SET lease_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",
        taskId);
    jdbc.update("UPDATE analysis_task SET attempt_count=max_attempts WHERE id=?", taskId);
    assertThat(tasks.failExpiredExhausted(taskId, now.plusSeconds(2))).isOne();
    assertThat(tasks.failExpiredExhausted(taskId, now.plusSeconds(2))).isZero();
  }

  @Test
  void outboxEventKeyIsIdempotentAndClaimIsConditional() {
    LocalDateTime now = LocalDateTime.parse("2026-01-01T00:00:00");
    assertThat(outbox.insert("test:" + taskId, "TASK_PUBLISH", "TASK", taskId, "{}", now)).isOne();
    assertThat(outbox.insert("test:" + taskId, "TASK_PUBLISH", "TASK", taskId, "{}", now)).isZero();
    var row = outbox.next("TASK_PUBLISH", now);
    assertThat(outbox.claim(row.getId(), "claim-a", now)).isOne();
    assertThat(outbox.claim(row.getId(), "claim-b", now)).isZero();
    assertThat(outbox.complete(row.getId(), "claim-b", now)).isZero();
    assertThat(outbox.complete(row.getId(), "claim-a", now)).isOne();
  }

  @Test
  void outboxRecoveryAndAvailabilityUseDatabaseClock() {
    LocalDateTime oldJvmTime = LocalDateTime.parse("2000-01-01T00:00:00");
    LocalDateTime futureJvmTime = LocalDateTime.parse("2999-01-01T00:00:00");
    assertThat(outbox.insert("clock:" + taskId, "CLOCK_TEST", "TASK", taskId, "{}", oldJvmTime))
        .isOne();
    var row = outbox.next("CLOCK_TEST", oldJvmTime);
    assertThat(row).isNotNull();
    assertThat(outbox.claim(row.getId(), "clock-claim", oldJvmTime)).isOne();
    assertThat(outbox.recover(LocalDateTime.parse("2999-01-01T00:00:00"), oldJvmTime)).isZero();
    jdbc.update(
        "UPDATE reliable_outbox SET claimed_at=UTC_TIMESTAMP(6)-INTERVAL 6 MINUTE WHERE id=?",
        row.getId());
    assertThat(outbox.recover(oldJvmTime, oldJvmTime)).isOne();
    assertThat(outbox.claim(row.getId(), "retry-claim", futureJvmTime)).isOne();
    assertThat(outbox.retry(row.getId(), "retry-claim", 30, "retry")).isOne();
    Integer delay =
        jdbc.queryForObject(
            "SELECT TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(6),available_at) FROM reliable_outbox WHERE"
                + " id=?",
            Integer.class,
            row.getId());
    assertThat(delay).isBetween(28, 30);

    assertThat(
            outbox.insert(
                "future:" + taskId, "FUTURE_CLOCK_TEST", "TASK", taskId, "{}", futureJvmTime))
        .isOne();
    assertThat(outbox.next("FUTURE_CLOCK_TEST", oldJvmTime)).isNotNull();
  }

  @Test
  void pendingDeletionIsNotOwnedActive() {
    jdbc.update("UPDATE dataset SET delete_status='DELETE_PENDING' WHERE id=?", datasetId);
    assertThat(datasets.countOwnedActive(datasetId, 900001)).isZero();
  }
}
