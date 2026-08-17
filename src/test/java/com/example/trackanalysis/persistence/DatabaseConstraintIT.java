package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseConstraintIT extends MySqlIntegrationTestSupport {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void tablesColumnsAndDefaultsMatchTheMigrationContract() {
    List<String> tables =
        jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
            ORDER BY table_name
            """,
            String.class);
    assertThat(tables)
        .containsExactly(
            "abnormal_interval",
            "algorithm_project",
            "algorithm_submission",
            "analysis_report",
            "analysis_result",
            "analysis_task",
            "audit_log",
            "benchmark",
            "benchmark_version",
            "dataset",
            "evaluation_protocol",
            "evaluation_run",
            "flyway_schema_history",
            "quality_gate",
            "reliable_outbox",
            "sys_role",
            "sys_user",
            "sys_user_role",
            "track_file",
            "track_point");

    Map<String, Map<String, Object>> taskColumns = columnsByName("analysis_task");
    assertThat(taskColumns)
        .containsOnlyKeys(
            "id",
            "track_file_id",
            "abnormal_threshold",
            "status",
            "lease_owner",
            "lease_token",
            "lease_expires_at",
            "heartbeat_at",
            "attempt_count",
            "max_attempts",
            "analysis_result_id",
            "error_message",
            "version",
            "started_at",
            "finished_at",
            "created_at",
            "updated_at");
    assertColumn(taskColumns, "status", "varchar(16)", "NO", null);
    assertColumn(taskColumns, "attempt_count", "int unsigned", "NO", "0");
    assertColumn(taskColumns, "max_attempts", "int unsigned", "NO", "3");

    Map<String, Map<String, Object>> userColumns = columnsByName("sys_user");
    assertThat(userColumns)
        .containsOnlyKeys(
            "id",
            "username",
            "display_name",
            "email",
            "password_hash",
            "status",
            "failed_login_count",
            "last_login_at",
            "auth_version",
            "version",
            "deleted",
            "created_at",
            "updated_at");
    assertColumn(userColumns, "id", "bigint", "NO", null);
    assertColumn(userColumns, "username", "varchar(64)", "NO", null);
    assertColumn(userColumns, "password_hash", "varchar(255)", "NO", null);
    assertColumn(userColumns, "status", "varchar(16)", "NO", "ACTIVE");
    assertColumn(userColumns, "auth_version", "int unsigned", "NO", "0");
    assertColumn(userColumns, "version", "int unsigned", "NO", "0");
    assertColumn(userColumns, "deleted", "tinyint unsigned", "NO", "0");
    assertColumn(userColumns, "created_at", "datetime(6)", "NO", "CURRENT_TIMESTAMP(6)");
    assertColumn(userColumns, "updated_at", "datetime(6)", "NO", "CURRENT_TIMESTAMP(6)");
    assertThat(userColumns.get("updated_at").get("extra").toString().toLowerCase())
        .doesNotContain("on update");

    Map<String, Map<String, Object>> datasetColumns = columnsByName("dataset");
    assertThat(datasetColumns)
        .containsOnlyKeys(
            "id",
            "user_id",
            "name",
            "description",
            "version",
            "deleted",
            "delete_status",
            "delete_requested_at",
            "deleted_at",
            "delete_error",
            "delete_attempt_count",
            "created_at",
            "updated_at");
    assertColumn(datasetColumns, "id", "bigint", "NO", null);
    assertColumn(datasetColumns, "user_id", "bigint", "NO", null);
    assertColumn(datasetColumns, "name", "varchar(128)", "NO", null);
    assertColumn(datasetColumns, "description", "varchar(500)", "YES", null);
    assertColumn(datasetColumns, "version", "int unsigned", "NO", "0");
    assertColumn(datasetColumns, "deleted", "tinyint unsigned", "NO", "0");
    assertColumn(datasetColumns, "created_at", "datetime(6)", "NO", "CURRENT_TIMESTAMP(6)");
    assertColumn(datasetColumns, "updated_at", "datetime(6)", "NO", "CURRENT_TIMESTAMP(6)");
    assertThat(datasetColumns.get("updated_at").get("extra").toString().toLowerCase())
        .doesNotContain("on update");
  }

  @Test
  void primaryUniqueForeignKeyAndOwnerPageIndexMatchTheContract() {
    assertThat(indexColumns("sys_user", "PRIMARY")).containsExactly("id");
    assertThat(indexColumns("sys_user", "uk_sys_user_username")).containsExactly("username");
    assertThat(indexColumns("dataset", "PRIMARY")).containsExactly("id");
    assertThat(indexColumns("dataset", "idx_dataset_owner_page"))
        .containsExactly("user_id", "deleted", "created_at", "id");
    assertThat(indexDirections("dataset", "idx_dataset_owner_page"))
        .containsExactly("A", "A", "D", "D");

    Map<String, Object> foreignKey =
        jdbcTemplate.queryForMap(
            """
            SELECT referenced_table_name, referenced_column_name
            FROM information_schema.key_column_usage
            WHERE table_schema = DATABASE()
              AND table_name = 'dataset'
              AND constraint_name = 'fk_dataset_user'
            """);
    assertThat(foreignKey.get("referenced_table_name")).isEqualTo("sys_user");
    assertThat(foreignKey.get("referenced_column_name")).isEqualTo("id");
    Map<String, Object> rules =
        jdbcTemplate.queryForMap(
            """
            SELECT update_rule, delete_rule
            FROM information_schema.referential_constraints
            WHERE constraint_schema = DATABASE()
              AND table_name = 'dataset'
              AND constraint_name = 'fk_dataset_user'
            """);
    assertThat(rules.get("update_rule")).isEqualTo("RESTRICT");
    assertThat(rules.get("delete_rule")).isEqualTo("RESTRICT");
  }

  @Test
  void usernameUniquenessIsCaseInsensitiveAndPermanent() {
    insertUser("CaseUser");
    jdbcTemplate.update("UPDATE sys_user SET deleted = 1 WHERE username = ?", "CaseUser");
    assertThatThrownBy(() -> insertUser("caseuser"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void requiredColumnsRejectNullAndForeignKeyRejectsOrphanDataset() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO sys_user (username, password_hash) VALUES (NULL, 'hash')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void foreignKeyRejectsOrphanDataset() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO dataset (user_id, name) VALUES (?, ?)", 999999L, "orphan"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void statusCheckConstraintRejectsInvalidState() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO sys_user (username, password_hash, status) VALUES (?, ?, ?)",
                    "invalid-status",
                    "hash",
                    "LOCKED"))
        .isInstanceOf(DataAccessException.class)
        .hasRootCauseInstanceOf(java.sql.SQLException.class);
  }

  @Test
  void deletedCheckConstraintsRejectInvalidFlags() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO sys_user (username, password_hash, deleted) VALUES (?, ?, ?)",
                    "invalid-deleted",
                    "hash",
                    2))
        .isInstanceOf(DataAccessException.class)
        .hasRootCauseInstanceOf(java.sql.SQLException.class);
  }

  @Test
  void datasetDeletedCheckConstraintRejectsInvalidFlag() {
    insertUser("dataset-check-owner");
    Long userId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM sys_user WHERE username = ?", Long.class, "dataset-check-owner");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO dataset (user_id, name, deleted) VALUES (?, ?, ?)",
                    userId,
                    "invalid-deleted",
                    2))
        .isInstanceOf(DataAccessException.class)
        .hasRootCauseInstanceOf(java.sql.SQLException.class);
  }

  @Test
  void trackFileAndPointConstraintsRejectInvalidAndDuplicateData() {
    insertUser("track-constraint-owner");
    Long userId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM sys_user WHERE username = ?", Long.class, "track-constraint-owner");
    jdbcTemplate.update("INSERT INTO dataset (user_id, name) VALUES (?, ?)", userId, "tracks");
    Long datasetId =
        jdbcTemplate.queryForObject("SELECT id FROM dataset WHERE user_id = ?", Long.class, userId);
    jdbcTemplate.update(
        """
        INSERT INTO track_file
        (dataset_id, original_name, object_name, sha256, file_size, track_source, parse_status)
        VALUES (?, 'one.csv', '1/1/one.csv', ?, 10, 'RADAR', 'UPLOADED')
        """,
        datasetId,
        "a".repeat(64));
    Long fileId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM track_file WHERE dataset_id = ?", Long.class, datasetId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO track_file
                    (dataset_id, original_name, object_name, sha256, file_size, track_source, parse_status)
                    VALUES (?, 'two.csv', '1/1/two.csv', ?, 10, 'OTHER', 'UPLOADED')
                    """,
                    datasetId,
                    "a".repeat(64)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE track_file SET parse_status = 'UNKNOWN' WHERE id = ?", fileId))
        .isInstanceOf(DataAccessException.class);

    jdbcTemplate.update(
        """
        INSERT INTO track_point
        (track_file_id, sequence_no, time_value, true_x, true_y, true_z, track_x, track_y, track_z)
        VALUES (?, 1, 1, 2, 3, 4, 5, 6, 7)
        """,
        fileId);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO track_point
                    (track_file_id, sequence_no, time_value, true_x, true_y, true_z, track_x, track_y, track_z)
                    VALUES (?, 1, 2, 2, 3, 4, 5, 6, 7)
                    """,
                    fileId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void analysisResultChecksAndForeignKeyRejectInvalidRows() {
    long fileId = insertParsedFile("analysis-constraint-owner", "analysis-constraint.csv");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO analysis_result
                    (track_file_id,abnormal_threshold,point_count,mean_error,rmse,min_error,max_error,
                     standard_deviation,abnormal_count,abnormal_ratio,max_error_time)
                    VALUES (?, -1, 1, 0, 0, 0, 0, 0, 0, 0, 1)
                    """,
                    fileId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO analysis_result
                    (track_file_id,abnormal_threshold,point_count,mean_error,rmse,min_error,max_error,
                     standard_deviation,abnormal_count,abnormal_ratio,max_error_time)
                    VALUES (?, 0, 1, 0, 0, 0, 0, 0, 2, 2, 1)
                    """,
                    fileId))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void abnormalIntervalChecksUniquenessAndForeignKey() {
    long fileId = insertParsedFile("interval-constraint-owner", "interval-constraint.csv");
    jdbcTemplate.update(
        """
        INSERT INTO analysis_result
        (track_file_id,abnormal_threshold,point_count,mean_error,rmse,min_error,max_error,
         standard_deviation,abnormal_count,abnormal_ratio,max_error_time)
        VALUES (?, 0, 1, 1, 1, 1, 1, 0, 1, 1, 1)
        """,
        fileId);
    long resultId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM analysis_result WHERE track_file_id = ?", Long.class, fileId);
    jdbcTemplate.update(
        """
        INSERT INTO abnormal_interval
        (analysis_result_id,interval_no,start_sequence,end_sequence,start_time,end_time,
         point_count,peak_error,peak_error_time)
        VALUES (?,1,1,1,1,1,1,1,1)
        """,
        resultId);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO abnormal_interval
                    (analysis_result_id,interval_no,start_sequence,end_sequence,start_time,end_time,
                     point_count,peak_error,peak_error_time)
                    VALUES (?,1,2,1,2,1,0,-1,1)
                    """,
                    resultId))
        .isInstanceOf(DataAccessException.class);
  }

  private long insertParsedFile(String username, String name) {
    insertUser(username);
    Long userId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM sys_user WHERE username = ?", Long.class, username);
    jdbcTemplate.update("INSERT INTO dataset (user_id,name) VALUES (?,?)", userId, name);
    Long datasetId =
        jdbcTemplate.queryForObject("SELECT id FROM dataset WHERE user_id = ?", Long.class, userId);
    jdbcTemplate.update(
        """
        INSERT INTO track_file
        (dataset_id,original_name,object_name,sha256,file_size,track_source,parse_status,point_count)
        VALUES (?,?,?, ?,10,'RADAR','PARSED',1)
        """,
        datasetId,
        name,
        username + "/file.csv",
        ("a" + Integer.toHexString(username.hashCode())).repeat(64).substring(0, 64));
    return jdbcTemplate.queryForObject(
        "SELECT id FROM track_file WHERE dataset_id = ?", Long.class, datasetId);
  }

  private Map<String, Map<String, Object>> columnsByName(String tableName) {
    return jdbcTemplate.query(
        """
        SELECT column_name, column_type, is_nullable, column_default, extra
        FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = ?
        """,
        resultSet -> {
          Map<String, Map<String, Object>> columns = new LinkedHashMap<>();
          while (resultSet.next()) {
            String columnDefault = resultSet.getString("column_default");
            columns.put(
                resultSet.getString("column_name"),
                Map.of(
                    "column_type", resultSet.getString("column_type"),
                    "is_nullable", resultSet.getString("is_nullable"),
                    "column_default", columnDefault == null ? NullValue.INSTANCE : columnDefault,
                    "extra", resultSet.getString("extra")));
          }
          return columns;
        },
        tableName);
  }

  private void assertColumn(
      Map<String, Map<String, Object>> columns,
      String name,
      String type,
      String nullable,
      String defaultValue) {
    assertThat(columns).containsKey(name);
    assertThat(columns.get(name).get("column_type")).isEqualTo(type);
    assertThat(columns.get(name).get("is_nullable")).isEqualTo(nullable);
    Object actualDefault = columns.get(name).get("column_default");
    assertThat(actualDefault == NullValue.INSTANCE ? null : actualDefault).isEqualTo(defaultValue);
  }

  private List<String> indexColumns(String tableName, String indexName) {
    return jdbcTemplate.queryForList(
        """
        SELECT column_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
        ORDER BY seq_in_index
        """,
        String.class,
        tableName,
        indexName);
  }

  private List<String> indexDirections(String tableName, String indexName) {
    return jdbcTemplate.queryForList(
        """
        SELECT collation
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
        ORDER BY seq_in_index
        """,
        String.class,
        tableName,
        indexName);
  }

  private void insertUser(String username) {
    jdbcTemplate.update(
        "INSERT INTO sys_user (username, password_hash) VALUES (?, ?)", username, "hash");
  }

  private enum NullValue {
    INSTANCE
  }
}
