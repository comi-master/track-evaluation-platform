package com.example.trackanalysis.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(PingController.class)
class PingControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void returnsUniformSuccessEnvelopeAndPreservesSafeRequestId() throws Exception {
    String requestId = "request-12345678";

    mockMvc
        .perform(get("/api/v1/ping").header(RequestIdFilter.REQUEST_ID_HEADER, requestId))
        .andExpect(status().isOk())
        .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, requestId))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.message").value("success"))
        .andExpect(jsonPath("$.requestId").value(requestId))
        .andExpect(jsonPath("$.data.status").value("ok"))
        .andExpect(jsonPath("$.data.application").value("track-analysis-platform"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  void replacesUnsafeRequestId() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/ping").header(RequestIdFilter.REQUEST_ID_HEADER, "bad value"))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        RequestIdFilter.REQUEST_ID_HEADER,
                        matchesPattern(
                            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andReturn();

    assertGeneratedRequestIdIsConsistent(result);
  }

  @Test
  void generatesConsistentRequestIdWhenHeaderIsMissing() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/ping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andReturn();

    assertGeneratedRequestIdIsConsistent(result);
  }

  private void assertGeneratedRequestIdIsConsistent(MvcResult result) throws Exception {
    String headerRequestId = result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER);
    String bodyRequestId = JsonPath.read(result.getResponse().getContentAsString(), "$.requestId");

    assertThat(headerRequestId)
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    assertThat(bodyRequestId).isEqualTo(headerRequestId);
  }
}
