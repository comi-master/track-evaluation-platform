package com.example.trackanalysis.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class SecurityJsonHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void authenticationFailureUsesTheUniformJsonEnvelope() throws Exception {
    MockHttpServletRequest request = request("security-request-12345678");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new RestAuthenticationEntryPoint(objectMapper)
        .commence(request, response, new BadCredentialsException("private detail"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .contains("\"code\":\"UNAUTHORIZED\"")
        .contains("\"requestId\":\"security-request-12345678\"")
        .doesNotContain("private detail");
  }

  @Test
  void accessDeniedUsesTheUniformJsonEnvelope() throws Exception {
    MockHttpServletRequest request = request("security-request-87654321");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new RestAccessDeniedHandler(objectMapper)
        .handle(request, response, new AccessDeniedException("private detail"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .contains("\"code\":\"FORBIDDEN\"")
        .contains("\"requestId\":\"security-request-87654321\"")
        .doesNotContain("private detail");
  }

  private MockHttpServletRequest request(String requestId) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, requestId);
    return request;
  }
}
