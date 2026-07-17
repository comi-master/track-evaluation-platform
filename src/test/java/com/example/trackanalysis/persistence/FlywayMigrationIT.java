package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayMigrationIT extends MySqlIntegrationTestSupport {

  @Autowired private Flyway flyway;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void migratesEmptyDatabaseAndRecordsChecksums() {
    List<Map<String, Object>> history =
        jdbcTemplate.queryForList(
            """
            SELECT version, success, checksum
            FROM flyway_schema_history
            WHERE version IS NOT NULL
            ORDER BY installed_rank
            """);

    assertThat(history).hasSize(9);
    assertThat(history)
        .extracting(row -> row.get("version"))
        .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
    assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
    assertThat(history).allSatisfy(row -> assertThat(row.get("checksum")).isNotNull());
  }

  @Test
  void repeatedMigrationDoesNotApplyAnotherVersion() {
    int before =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL", Integer.class);

    MigrateResult result = flyway.migrate();

    int after =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL", Integer.class);
    assertThat(result.migrationsExecuted).isZero();
    assertThat(after).isEqualTo(before);
  }

  @Test
  void databaseSessionUsesUtc() {
    assertThat(jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class))
        .isEqualTo("+00:00");
  }
}
