package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.trackanalysis.analysis.application.AnalysisApplicationService;
import com.example.trackanalysis.analysis.application.AnalysisCacheService;
import com.example.trackanalysis.messaging.AnalysisTaskPublisher;
import com.example.trackanalysis.messaging.RabbitTopologyConfig;
import com.example.trackanalysis.task.application.AnalysisTaskApplicationService;
import com.example.trackanalysis.task.domain.AnalysisTaskStatus;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AsyncAnalysisInfrastructureIT extends MySqlIntegrationTestSupport {
  private static final String REDIS_PASSWORD = "it_redis_password_123";

  @Container
  static final RabbitMQContainer RABBIT =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.2.8-management"));

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:8.2.7-alpine"))
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

  @DynamicPropertySource
  static void middlewareProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> true);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
    registry.add("track.task.retry-delay-milliseconds", () -> 200L);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired AnalysisTaskApplicationService tasks;
  @Autowired AnalysisCacheService cache;
  @Autowired AnalysisApplicationService analysis;
  @Autowired AnalysisTaskPublisher publisher;
  @Autowired StringRedisTemplate redis;
  @Autowired RabbitAdmin rabbit;

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM analysis_task");
    jdbc.update("DELETE FROM abnormal_interval");
    jdbc.update("DELETE FROM analysis_result");
    jdbc.update("DELETE FROM track_point");
    jdbc.update("DELETE FROM track_file");
    jdbc.update("DELETE FROM dataset");
    jdbc.update("DELETE FROM sys_user");
  }

  @Test
  void durableTopologyExistsAndAsyncTaskCompletesExactlyOnce() {
    long user = insertUser("async-" + UUID.randomUUID());
    long dataset = insertDataset(user);
    long file = insertFile(dataset, "parsed.csv", "PARSED");
    jdbc.update(
        "INSERT INTO track_point"
            + " (track_file_id,sequence_no,time_value,true_x,true_y,true_z,track_x,track_y,track_z)"
            + " VALUES (?,1,1,0,0,0,3,4,0)",
        file);

    long taskId = tasks.create(user, file, 4).taskId();
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(tasks.get(user, taskId).status()).isEqualTo(AnalysisTaskStatus.SUCCESS));
    var completed = tasks.get(user, taskId);
    assertThat(completed.attemptCount()).isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM analysis_result WHERE track_file_id=?", Integer.class, file))
        .isOne();
    assertThat(rabbit.getQueueInfo(RabbitTopologyConfig.QUEUE)).isNotNull();
    assertThat(rabbit.getQueueInfo(RabbitTopologyConfig.RETRY_QUEUE)).isNotNull();
    assertThat(rabbit.getQueueInfo(RabbitTopologyConfig.DEAD_QUEUE)).isNotNull();

    publisher.publish(taskId);
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForObject(
                            "SELECT COUNT(*) FROM analysis_result WHERE track_file_id=?",
                            Integer.class,
                            file))
                    .isOne());

    analysis.latest(user, file);
    analysis.comparison(user, dataset);
    String latestKey = "analysis:latest:" + user + ":" + file;
    String comparisonKey = "analysis:comparison:" + user + ":" + dataset;
    assertThat(redis.hasKey(latestKey)).isTrue();
    assertThat(redis.getExpire(latestKey)).isBetween(1L, 600L);
    assertThat(redis.hasKey(comparisonKey)).isTrue();
    assertThat(redis.getExpire(comparisonKey)).isBetween(1L, 300L);
    analysis.create(user, file, 6);
    assertThat(redis.hasKey(latestKey)).isFalse();
    assertThat(redis.hasKey(comparisonKey)).isFalse();
  }

  @Test
  void permanentFailureIsRecordedAndDeadLetteredAndCacheUsesOwnerScopedKeys() {
    long user = insertUser("dead-" + UUID.randomUUID());
    long dataset = insertDataset(user);
    long file = insertFile(dataset, "empty.csv", "PARSED");
    long taskId = tasks.create(user, file, 1).taskId();
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(tasks.get(user, taskId).status()).isEqualTo(AnalysisTaskStatus.FAILED));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              QueueInformation info = rabbit.getQueueInfo(RabbitTopologyConfig.DEAD_QUEUE);
              assertThat(info).isNotNull();
              assertThat(info.getMessageCount()).isGreaterThanOrEqualTo(1);
            });
    assertThat(cache.latest(user, file)).isEmpty();
    assertThat(cache.latest(user + 1, file)).isEmpty();
  }

  private long insertUser(String username) {
    jdbc.update("INSERT INTO sys_user (username,password_hash) VALUES (?, 'hash')", username);
    return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
  }

  private long insertDataset(long user) {
    jdbc.update("INSERT INTO dataset (user_id,name) VALUES (?,?)", user, "dataset-" + user);
    return jdbc.queryForObject("SELECT id FROM dataset WHERE user_id=?", Long.class, user);
  }

  private long insertFile(long dataset, String name, String status) {
    jdbc.update(
        "INSERT INTO track_file"
            + " (dataset_id,original_name,object_name,sha256,file_size,track_source,parse_status,point_count)"
            + " VALUES (?,?,?, ?,1,'RADAR',?,0)",
        dataset,
        name,
        "it/" + name,
        UUID.randomUUID().toString().replace("-", "").repeat(2),
        status);
    return jdbc.queryForObject(
        "SELECT id FROM track_file WHERE dataset_id=? AND original_name=?",
        Long.class,
        dataset,
        name);
  }
}
