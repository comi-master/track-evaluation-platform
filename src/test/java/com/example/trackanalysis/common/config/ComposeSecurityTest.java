package com.example.trackanalysis.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ComposeSecurityTest {

  private static final Path COMPOSE_FILE = Path.of("compose.yaml");

  @Test
  void healthChecksUseEnvironmentAuthenticationInsteadOfPasswordArguments() throws IOException {
    String compose = Files.readString(COMPOSE_FILE);

    assertThat(compose)
        .contains("MYSQL_PWD=\\\"$${MYSQL_ROOT_PASSWORD}\\\" mysqladmin ping")
        .contains("REDISCLI_AUTH=\\\"$${REDIS_PASSWORD}\\\" redis-cli")
        .doesNotContain("mysqladmin ping -h localhost -u root -p")
        .doesNotContain("redis-cli --no-auth-warning -a");
  }

  @Test
  void redisCommandUsesTheValidatedContainerEntrypoint() throws IOException {
    String compose = Files.readString(COMPOSE_FILE);

    assertThat(compose)
        .contains("command: [\"sh\", \"/usr/local/bin/track-redis-entrypoint.sh\"]")
        .contains("./docker/redis/entrypoint.sh:/usr/local/bin/track-redis-entrypoint.sh:ro")
        .doesNotContain("printf 'requirepass %s\\n' \"$${REDIS_PASSWORD}\"");
  }
}
