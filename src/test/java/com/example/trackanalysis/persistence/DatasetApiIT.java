package com.example.trackanalysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class DatasetApiIT extends MySqlIntegrationTestSupport {

  @Autowired private MockMvc mockMvc;

  @Test
  void authenticatedUserCanCreateReadUpdateListAndLogicallyDeleteADataset() throws Exception {
    String token = registerAndLogin("dataset-user");
    long id = create(token, " First dataset ", "  description  ");

    mockMvc
        .perform(get("/api/v1/datasets/{id}", id).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("First dataset"))
        .andExpect(jsonPath("$.data.description").value("description"))
        .andExpect(jsonPath("$.data.version").value(0));

    mockMvc
        .perform(
            put("/api/v1/datasets/{id}", id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Updated dataset","description":"updated","version":0}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Updated dataset"))
        .andExpect(jsonPath("$.data.version").value(1));

    mockMvc
        .perform(get("/api/v1/datasets").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(id));

    mockMvc
        .perform(delete("/api/v1/datasets/{id}", id).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUCCESS"));
    mockMvc
        .perform(get("/api/v1/datasets/{id}", id).header("Authorization", bearer(token)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/api/v1/datasets/{id}", id).header("Authorization", bearer(token)))
        .andExpect(status().isNotFound());
  }

  @Test
  void ownerIsolationReturns404ForReadUpdateAndDelete() throws Exception {
    String ownerToken = registerAndLogin("owner-user");
    String otherToken = registerAndLogin("other-user");
    long id = create(ownerToken, "private dataset", null);

    mockMvc
        .perform(get("/api/v1/datasets/{id}", id).header("Authorization", bearer(otherToken)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            put("/api/v1/datasets/{id}", id)
                .header("Authorization", bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"hijack\",\"description\":null,\"version\":0}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/api/v1/datasets/{id}", id).header("Authorization", bearer(otherToken)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/v1/datasets/{id}", id).header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("private dataset"));
  }

  @Test
  void staleUpdateReturns409InsteadOf404OrSuccess() throws Exception {
    String token = registerAndLogin("version-user");
    long id = create(token, "before", null);

    update(token, id, "first update", 0).andExpect(status().isOk());
    update(token, id, "stale update", 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    mockMvc
        .perform(get("/api/v1/datasets/{id}", id).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("first update"))
        .andExpect(jsonPath("$.data.version").value(1));
  }

  @Test
  void paginationKeywordAndStableNewestFirstOrderingAreEnforced() throws Exception {
    String token = registerAndLogin("page-user");
    long first = create(token, "alpha first", null);
    long second = create(token, "beta second", null);
    long third = create(token, "alpha third", null);

    MvcResult firstPage =
        mockMvc
            .perform(
                get("/api/v1/datasets")
                    .header("Authorization", bearer(token))
                    .queryParam("page", "1")
                    .queryParam("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andReturn();
    assertThat(
            (Number)
                JsonPath.read(firstPage.getResponse().getContentAsString(), "$.data.items[0].id"))
        .extracting(Number::longValue)
        .isEqualTo(third);
    assertThat(
            (Number)
                JsonPath.read(firstPage.getResponse().getContentAsString(), "$.data.items[1].id"))
        .extracting(Number::longValue)
        .isEqualTo(second);

    mockMvc
        .perform(
            get("/api/v1/datasets")
                .header("Authorization", bearer(token))
                .queryParam("keyword", "alpha"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2))
        .andExpect(jsonPath("$.data.items[0].id").value(third))
        .andExpect(jsonPath("$.data.items[1].id").value(first));
    mockMvc
        .perform(
            get("/api/v1/datasets")
                .header("Authorization", bearer(token))
                .queryParam("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void keywordTreatsSqlWildcardCharactersLiterally() throws Exception {
    String token = registerAndLogin("wildcard-user");
    long literal = create(token, "percent%dataset", null);
    create(token, "percentXdataset", null);

    mockMvc
        .perform(
            get("/api/v1/datasets")
                .header("Authorization", bearer(token))
                .queryParam("keyword", "%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(literal));
  }

  private String registerAndLogin(String username) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"%s","password":"correct-password"}
                    """
                        .formatted(username)))
        .andExpect(status().isCreated());
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"username":"%s","password":"correct-password"}
                        """
                            .formatted(username)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
  }

  private long create(String token, String name, String description) throws Exception {
    String json =
        description == null
            ? "{\"name\":\"%s\",\"description\":null}".formatted(name)
            : "{\"name\":\"%s\",\"description\":\"%s\"}".formatted(name, description);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/datasets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn();
    return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"))
        .longValue();
  }

  private org.springframework.test.web.servlet.ResultActions update(
      String token, long id, String name, int version) throws Exception {
    return mockMvc.perform(
        put("/api/v1/datasets/{id}", id)
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"name":"%s","description":null,"version":%d}
                """
                    .formatted(name, version)));
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
