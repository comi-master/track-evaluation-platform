package com.example.trackanalysis.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
    classes = RedisIndexedSessionIT.Configuration.class,
    properties =
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
class RedisIndexedSessionIT {
  private static final String PASSWORD = "redis_it_password_123";

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:8.2.7-alpine"))
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", PASSWORD);

  @DynamicPropertySource
  static void redis(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> PASSWORD);
  }

  @SuppressWarnings("rawtypes")
  @org.springframework.beans.factory.annotation.Autowired
  FindByIndexNameSessionRepository repository;

  @Test
  @SuppressWarnings("unchecked")
  void savesSessionsAndBuildsPrincipalIndexForMultipleUsers() {
    Session first = create("researcher-a", Duration.ofMinutes(5));
    Session second = create("researcher-a", Duration.ofMinutes(5));
    create("researcher-b", Duration.ofMinutes(5));

    Map<String, ? extends Session> a = repository.findByPrincipalName("researcher-a");
    Map<String, ? extends Session> b = repository.findByPrincipalName("researcher-b");
    assertThat(a).hasSize(2).containsKeys(first.getId(), second.getId());
    assertThat(b).hasSize(1);
  }

  @Test
  void precisePrincipalCleanupLeavesOtherUsersUntouched() {
    create("cleanup-target", Duration.ofMinutes(5));
    create("cleanup-target", Duration.ofMinutes(5));
    Session retained = create("cleanup-retained", Duration.ofMinutes(5));
    for (Object id : repository.findByPrincipalName("cleanup-target").keySet()) {
      repository.deleteById((String) id);
    }

    assertThat(repository.findByPrincipalName("cleanup-target")).isEmpty();
    assertThat(repository.findById(retained.getId())).isNotNull();
  }

  @Test
  void expiryRemovesSessionAndPrincipalIndex() throws Exception {
    Session expiring = create("expires", Duration.ofSeconds(1));
    long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
    while (repository.findById(expiring.getId()) != null && System.nanoTime() < deadline) {
      Thread.sleep(100);
    }
    assertThat(repository.findById(expiring.getId())).isNull();
    assertThat(repository.findByPrincipalName("expires")).isEmpty();
  }

  @Test
  void explicitLogoutDeletionRemovesOnlyThatSession() {
    Session removed = create("logout-user", Duration.ofMinutes(5));
    Session retained = create("logout-user", Duration.ofMinutes(5));
    repository.deleteById(removed.getId());
    assertThat(repository.findById(removed.getId())).isNull();
    assertThat(repository.findById(retained.getId())).isNotNull();
  }

  @SuppressWarnings("unchecked")
  private Session create(String principal, Duration ttl) {
    Session session = (Session) repository.createSession();
    session.setMaxInactiveInterval(ttl);
    session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal);
    repository.save(session);
    return session;
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableRedisIndexedHttpSession(redisNamespace = "track-analysis:redis-it")
  static class Configuration {}
}
