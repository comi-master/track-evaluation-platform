package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = false)
class V2ToV3MigrationIT {

  @Container
  private static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.4.10"))
          .withDatabaseName("track_analysis_upgrade_it")
          .withUsername("track_upgrade_it")
          .withPassword(UUID.randomUUID().toString());

  @Test
  void upgradesAnExistingV2SchemaToV3WithoutChangingEarlierMigrations() {
    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .target(MigrationVersion.fromVersion("2"))
        .load()
        .migrate();
    JdbcTemplate jdbcTemplate =
        new JdbcTemplate(
            new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    assertThat(columnCount(jdbcTemplate, "auth_version")).isZero();
    jdbcTemplate.update(
        "INSERT INTO sys_user (username, password_hash) VALUES (?, ?)",
        "legacy-user",
        "legacy-bcrypt-hash");

    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .load()
        .migrate();

    assertThat(columnCount(jdbcTemplate, "auth_version")).isOne();
    var metadata =
        jdbcTemplate.queryForMap(
            """
            SELECT column_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'sys_user'
              AND column_name = 'auth_version'
            """);
    assertThat(metadata.get("column_type")).isEqualTo("int unsigned");
    assertThat(metadata.get("is_nullable")).isEqualTo("NO");
    assertThat(metadata.get("column_default").toString()).isEqualTo("0");
    var legacyUser =
        jdbcTemplate.queryForMap(
            "SELECT username, password_hash, auth_version FROM sys_user WHERE username = ?",
            "legacy-user");
    assertThat(legacyUser)
        .containsEntry("username", "legacy-user")
        .containsEntry("password_hash", "legacy-bcrypt-hash")
        .containsEntry("auth_version", 0L);
  }

  private Integer columnCount(JdbcTemplate jdbcTemplate, String columnName) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND column_name = ?
        """,
        Integer.class,
        columnName);
  }
}
