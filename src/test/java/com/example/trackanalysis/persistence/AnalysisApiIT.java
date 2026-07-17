package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

class AnalysisApiIT extends MySqlIntegrationTestSupport {
  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate transactions;

  @Test
  void analyzesKnownValuesAndQueriesLatestHistoryIntervalsAndErrorSeries() throws Exception {
    String token = registerAndLogin("analysis-owner");
    long dataset = dataset(token, "analysis-data");
    long file = file(dataset, "known.csv", "RADAR", "PARSED");
    point(file, 1, 1, 0);
    point(file, 2, 2, 3);
    point(file, 3, 3, 4);

    MvcResult created = analyze(token, file, 2, "analysis-request-12345678");
    long analysisId =
        ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"))
            .longValue();
    mockMvc
        .perform(
            get("/api/v1/track-files/{id}/analyses/latest", file)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.meanError").value(7.0 / 3))
        .andExpect(jsonPath("$.data.rmse").value(Math.sqrt(25.0 / 3)))
        .andExpect(jsonPath("$.data.standardDeviation").value(Math.sqrt(26.0 / 9)))
        .andExpect(jsonPath("$.data.abnormalCount").value(2));
    mockMvc
        .perform(
            get("/api/v1/track-files/{id}/analyses", file).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    mockMvc
        .perform(
            get("/api/v1/analysis-results/{id}/abnormal-intervals", analysisId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].startSequence").value(2))
        .andExpect(jsonPath("$.data[0].endSequence").value(3));
    mockMvc
        .perform(
            get("/api/v1/track-files/{id}/error-series", file)
                .header("Authorization", bearer(token))
                .queryParam("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(3))
        .andExpect(jsonPath("$.data.records[1].error").value(3));
  }

  @Test
  void comparisonUsesLatestPerFileAndKeepsThreeSourcesSeparate() throws Exception {
    String token = registerAndLogin("comparison-owner");
    long dataset = dataset(token, "comparison-data");
    long radar = parsedFileWithPoint(dataset, "r.csv", "RADAR", 3);
    long infrared = parsedFileWithPoint(dataset, "i.csv", "INFRARED", 4);
    long fusion = parsedFileWithPoint(dataset, "f.csv", "FUSION", 5);
    analyze(token, radar, 1, null);
    analyze(token, radar, 10, null);
    analyze(token, infrared, 1, null);
    analyze(token, fusion, 1, null);
    mockMvc
        .perform(
            get("/api/v1/datasets/{id}/analysis-comparison", dataset)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(3))
        .andExpect(jsonPath("$.data[0].trackSource").value("RADAR"))
        .andExpect(jsonPath("$.data[0].abnormalThreshold").value(10))
        .andExpect(jsonPath("$.data[1].trackSource").value("INFRARED"))
        .andExpect(jsonPath("$.data[2].trackSource").value("FUSION"));
  }

  @Test
  void ownershipNonParsedAndThresholdValidationUseSafeStatuses() throws Exception {
    String owner = registerAndLogin("analysis-private-owner");
    String other = registerAndLogin("analysis-private-other");
    long dataset = dataset(owner, "private-data");
    long parsed = parsedFileWithPoint(dataset, "private.csv", "RADAR", 1);
    long uploaded = file(dataset, "pending.csv", "RADAR", "UPLOADED");
    mockMvc
        .perform(
            post("/api/v1/track-files/{id}/analyses", parsed)
                .header("Authorization", bearer(other))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"abnormalThreshold\":0}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/v1/track-files/{id}/analyses", uploaded)
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"abnormalThreshold\":0}"))
        .andExpect(status().isConflict());
    for (String body :
        new String[] {"{}", "{\"abnormalThreshold\":-1}", "{\"abnormalThreshold\":\"bad\"}"}) {
      mockMvc
          .perform(
              post("/api/v1/track-files/{id}/analyses", parsed)
                  .header("Authorization", bearer(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void invalidIntervalRollsBackResultAndIntervalsTogether() throws Exception {
    String token = registerAndLogin("analysis-rollback-owner");
    long dataset = dataset(token, "rollback-data");
    long file = parsedFileWithPoint(dataset, "rollback.csv", "RADAR", 1);
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    ignored -> {
                      jdbc.update(
                          """
                          INSERT INTO analysis_result
                          (track_file_id,abnormal_threshold,point_count,mean_error,rmse,min_error,
                           max_error,standard_deviation,abnormal_count,abnormal_ratio,max_error_time)
                          VALUES (?,0,1,1,1,1,1,0,1,1,1)
                          """,
                          file);
                      long result =
                          jdbc.queryForObject(
                              "SELECT id FROM analysis_result WHERE track_file_id=?",
                              Long.class,
                              file);
                      jdbc.update(
                          """
                          INSERT INTO abnormal_interval
                          (analysis_result_id,interval_no,start_sequence,end_sequence,start_time,
                           end_time,point_count,peak_error,peak_error_time)
                          VALUES (?,1,2,1,2,1,0,-1,1)
                          """,
                          result);
                    }))
        .isInstanceOf(RuntimeException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM analysis_result WHERE track_file_id=?", Integer.class, file))
        .isZero();
  }

  private MvcResult analyze(String token, long file, double threshold, String requestId)
      throws Exception {
    var request =
        post("/api/v1/track-files/{id}/analyses", file)
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"abnormalThreshold\":" + threshold + "}");
    if (requestId != null) request.header("X-Request-Id", requestId);
    var action = mockMvc.perform(request).andExpect(status().isCreated());
    if (requestId != null)
      action
          .andExpect(header().string("X-Request-Id", requestId))
          .andExpect(jsonPath("$.requestId").value(requestId));
    return action.andReturn();
  }

  private long parsedFileWithPoint(long dataset, String name, String source, double error) {
    long id = file(dataset, name, source, "PARSED");
    point(id, 1, 1, error);
    return id;
  }

  private long file(long dataset, String name, String source, String status) {
    jdbc.update(
        "INSERT INTO track_file"
            + " (dataset_id,original_name,object_name,sha256,file_size,track_source,parse_status,point_count)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        dataset,
        name,
        UUID.randomUUID() + ".csv",
        UUID.randomUUID().toString().replace("-", "").repeat(2),
        10,
        source,
        status,
        status.equals("PARSED") ? 1 : 0);
    return jdbc.queryForObject(
        "SELECT id FROM track_file WHERE dataset_id=? AND original_name=?",
        Long.class,
        dataset,
        name);
  }

  private void point(long file, long sequence, double time, double error) {
    jdbc.update(
        "INSERT INTO track_point"
            + " (track_file_id,sequence_no,time_value,true_x,true_y,true_z,track_x,track_y,track_z)"
            + " VALUES (?,?,?,?,?,?,?,?,?)",
        file,
        sequence,
        time,
        0,
        0,
        0,
        error,
        0,
        0);
    jdbc.update(
        "UPDATE track_file SET point_count=(SELECT COUNT(*) FROM track_point WHERE track_file_id=?)"
            + " WHERE id=?",
        file,
        file);
  }

  private String registerAndLogin(String username) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
        .andExpect(status().isCreated());
    MvcResult r =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(r.getResponse().getContentAsString(), "$.data.accessToken");
  }

  private long dataset(String token, String name) throws Exception {
    MvcResult r =
        mockMvc
            .perform(
                post("/api/v1/datasets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data.id")).longValue();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
