package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TrackFileApiIT extends MySqlIntegrationTestSupport {

  private static final String MINIO_USER = "track-test-access";
  private static final String MINIO_PASSWORD = "track-test-secret-password";
  private static final String HEADER = "time,true_x,true_y,true_z,track_x,track_y,track_z\n";

  @Container
  private static final GenericContainer<?> MINIO =
      new GenericContainer<>(DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z"))
          .withEnv("MINIO_ROOT_USER", MINIO_USER)
          .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
          .withCommand("server", "/data")
          .withExposedPorts(9000)
          .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

  @DynamicPropertySource
  static void minioProperties(DynamicPropertyRegistry registry) {
    registry.add("storage.minio.enabled", () -> true);
    registry.add(
        "storage.minio.endpoint",
        () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
    registry.add("storage.minio.access-key", () -> MINIO_USER);
    registry.add("storage.minio.secret-key", () -> MINIO_PASSWORD);
    registry.add("storage.minio.bucket", () -> "track-files-it");
  }

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void uploadsParsesAcrossBatchesAndQueriesStablePages() throws Exception {
    String token = registerAndLogin("track-owner");
    long datasetId = createDataset(token, "tracks");
    StringBuilder csv = new StringBuilder("\uFEFF").append(HEADER);
    for (int index = 1; index <= 501; index++) {
      csv.append(index).append(",1,2,3,4,5,6\n");
    }

    long fileId = upload(token, datasetId, "safe.CSV", csv.toString());
    mockMvc
        .perform(get("/api/v1/track-files/{id}", fileId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.parseStatus").value("UPLOADED"));
    mockMvc
        .perform(
            get("/api/v1/datasets/{id}/track-files", datasetId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));

    mockMvc
        .perform(
            post("/api/v1/track-files/{id}/parse", fileId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.parseStatus").value("PARSED"))
        .andExpect(jsonPath("$.data.pointCount").value(501));

    mockMvc
        .perform(
            get("/api/v1/track-files/{id}/points", fileId)
                .header("Authorization", bearer(token))
                .queryParam("page", "2")
                .queryParam("size", "500"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(501))
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].sequenceNo").value(501));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM track_point WHERE track_file_id = ?", Integer.class, fileId))
        .isEqualTo(501);
  }

  @Test
  void duplicateContentConflictsAndInvalidMiddleRowRollsBackAllPoints() throws Exception {
    String token = registerAndLogin("track-errors");
    long datasetId = createDataset(token, "error tracks");
    String valid = HEADER + "1,1,2,3,4,5,6\n";
    upload(token, datasetId, "one.csv", valid);
    mockMvc
        .perform(
            multipart("/api/v1/datasets/{id}/track-files", datasetId)
                .file(csv("again.csv", valid))
                .param("trackSource", "RADAR")
                .header("Authorization", bearer(token)))
        .andExpect(status().isConflict());

    StringBuilder invalidCsv = new StringBuilder(HEADER);
    for (int index = 1; index <= 500; index++) {
      invalidCsv.append(index).append(",1,2,3,4,5,6\n");
    }
    invalidCsv.append("501,1,bad,3,4,5,6\n");
    long invalidId = upload(token, datasetId, "invalid.csv", invalidCsv.toString());
    mockMvc
        .perform(
            post("/api/v1/track-files/{id}/parse", invalidId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CSV line 502")));
    mockMvc
        .perform(get("/api/v1/track-files/{id}", invalidId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.parseStatus").value("FAILED"))
        .andExpect(jsonPath("$.data.pointCount").value(0));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM track_point WHERE track_file_id = ?",
                Integer.class,
                invalidId))
        .isZero();
  }

  @Test
  void foreignResourcesAreIndistinguishableFromMissingResources() throws Exception {
    String owner = registerAndLogin("track-private-owner");
    String other = registerAndLogin("track-private-other");
    long datasetId = createDataset(owner, "private tracks");
    long fileId = upload(owner, datasetId, "private.csv", HEADER + "1,1,2,3,4,5,6\n");

    mockMvc
        .perform(
            multipart("/api/v1/datasets/{id}/track-files", datasetId)
                .file(csv("foreign.csv", HEADER + "2,1,2,3,4,5,6\n"))
                .param("trackSource", "OTHER")
                .header("Authorization", bearer(other)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/track-files/{id}", fileId).header("Authorization", bearer(other)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/v1/track-files/{id}/parse", fileId).header("Authorization", bearer(other)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/v1/track-files/{id}/points", fileId).header("Authorization", bearer(other)))
        .andExpect(status().isNotFound());
  }

  private long upload(String token, long datasetId, String name, String content) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/v1/datasets/{id}/track-files", datasetId)
                    .file(csv(name, content))
                    .param("trackSource", "RADAR")
                    .header("Authorization", bearer(token)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.sha256").isNotEmpty())
            .andReturn();
    return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"))
        .longValue();
  }

  private MockMultipartFile csv(String name, String content) {
    return new MockMultipartFile(
        "file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
  }

  private String registerAndLogin(String username) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
        .andExpect(status().isCreated());
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
  }

  private long createDataset(String token, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/datasets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"))
        .longValue();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
