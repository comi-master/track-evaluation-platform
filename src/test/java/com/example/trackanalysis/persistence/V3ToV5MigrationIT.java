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
class V3ToV5MigrationIT {

  @Container
  private static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.4.10"))
          .withDatabaseName("track_upgrade_v5_it")
          .withUsername("track_upgrade_v5_it")
          .withPassword(UUID.randomUUID().toString());

  @Test
  void upgradesV5DataToV7WithoutChangingExistingTrackData() {
    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .target(MigrationVersion.fromVersion("5"))
        .load()
        .migrate();
    JdbcTemplate jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    jdbc.update(
        "INSERT INTO sys_user (username, password_hash) VALUES (?, ?)",
        "preserved-user",
        "preserved-hash");
    Long userId =
        jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE username = 'preserved-user'", Long.class);
    jdbc.update("INSERT INTO dataset (user_id, name) VALUES (?, ?)", userId, "preserved-dataset");
    Long datasetId =
        jdbc.queryForObject("SELECT id FROM dataset WHERE name = 'preserved-dataset'", Long.class);
    jdbc.update(
        """
        INSERT INTO track_file
        (dataset_id, original_name, object_name, sha256, file_size, track_source,
         parse_status, point_count)
        VALUES (?, 'preserved.csv', 'preserved/object.csv', ?, 10, 'RADAR', 'PARSED', 1)
        """,
        datasetId,
        "f".repeat(64));
    Long fileId =
        jdbc.queryForObject(
            "SELECT id FROM track_file WHERE original_name = 'preserved.csv'", Long.class);
    jdbc.update(
        """
        INSERT INTO track_point
        (track_file_id, sequence_no, time_value, true_x, true_y, true_z, track_x, track_y, track_z)
        VALUES (?, 1, 1, 0, 0, 0, 3, 0, 0)
        """,
        fileId);

    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .load()
        .migrate();

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = 'preserved-user'", Integer.class))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataset WHERE name = 'preserved-dataset'", Integer.class))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND"
                    + " table_name IN"
                    + " ('track_file','track_point','analysis_result','abnormal_interval')",
                Integer.class))
        .isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM track_point WHERE track_file_id = ?", Integer.class, fileId))
        .isOne();
  }
}
