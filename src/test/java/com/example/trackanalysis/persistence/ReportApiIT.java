package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class ReportApiIT extends MySqlIntegrationTestSupport {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void reportIsEscapedImmutableOwnerScopedAndDownloadable() throws Exception {
    String owner = token("report-owner");
    String other = token("report-other");
    long dataset = dataset(owner, "<b>A&B</b>");
    long file = file(dataset, "<img src=x onerror=alert(1)>.csv");
    result(file, 1, 8);
    result(file, 2, 2);

    MvcResult created =
        mvc.perform(
                post("/api/v1/datasets/{id}/reports", dataset)
                    .header("Authorization", bearer(owner))
                    .header("X-Request-Id", "report-request-12345678")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"<script>alert(1)</script>\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-Request-Id", "report-request-12345678"))
            .andExpect(jsonPath("$.data.sourceFileCount").value(1))
            .andReturn();
    long report =
        ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.reportId"))
            .longValue();
    String html =
        jdbc.queryForObject(
            "SELECT content_html FROM analysis_report WHERE id=?", String.class, report);
    assertThat(html)
        .contains("&lt;script&gt;", "&lt;b&gt;A&amp;B&lt;/b&gt;")
        .doesNotContain("<script>", "onerror=alert(1)>");
    assertThat(html).contains("<td>2.0</td>");

    mvc.perform(
            get("/api/v1/datasets/{id}/reports", dataset).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(get("/api/v1/reports/{id}", report).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reportId").value(report));
    mvc.perform(get("/api/v1/reports/{id}/content", report).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/html;charset=UTF-8"));
    mvc.perform(get("/api/v1/reports/{id}/download", report).header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    org.hamcrest.Matchers.containsString("analysis-report-" + report + ".html")));
    mvc.perform(get("/api/v1/reports/{id}", report).header("Authorization", bearer(other)))
        .andExpect(status().isNotFound());

    jdbc.update("UPDATE analysis_result SET rmse=999 WHERE track_file_id=?", file);
    assertThat(
            jdbc.queryForObject(
                "SELECT content_html FROM analysis_report WHERE id=?", String.class, report))
        .isEqualTo(html);
  }

  @Test
  void missingAnalysisAndBadTitleAreRejected() throws Exception {
    String token = token("report-empty");
    long dataset = dataset(token, "empty");
    mvc.perform(
            post("/api/v1/datasets/{id}/reports", dataset)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"valid\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/v1/datasets/{id}/reports", dataset)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \"}"))
        .andExpect(status().isBadRequest());
  }

  private String token(String username) throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
        .andExpect(status().isCreated());
    MvcResult r =
        mvc.perform(
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
        mvc.perform(
                post("/api/v1/datasets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name.replace("\"", "\\\"") + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data.id")).longValue();
  }

  private long file(long dataset, String name) {
    jdbc.update(
        "INSERT INTO"
            + " track_file(dataset_id,original_name,object_name,sha256,file_size,track_source,parse_status,point_count)"
            + " VALUES(?,?,?,?,10,'RADAR','PARSED',3)",
        dataset,
        name,
        UUID.randomUUID() + ".csv",
        UUID.randomUUID().toString().replace("-", "").repeat(2));
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM track_file WHERE dataset_id=?", Long.class, dataset);
  }

  private void result(long file, int sequence, double metric) {
    jdbc.update(
        "INSERT INTO"
            + " analysis_result(track_file_id,abnormal_threshold,point_count,mean_error,rmse,min_error,max_error,standard_deviation,abnormal_count,abnormal_ratio,max_error_time,created_at)"
            + " VALUES(?,2,3,?,?,0,?,1,1,0.333,3,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL ?"
            + " MICROSECOND))",
        file,
        metric,
        metric,
        metric,
        sequence);
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
