package com.example.trackanalysis.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @BeforeEach
  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void usesCallerRequestIdWhenItIsSafe() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "external-12345678");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE))
        .isEqualTo("external-12345678");
    assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
        .isEqualTo("external-12345678");
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void generatesRequestIdWhenCallerValueIsUnsafe() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "../../unsafe");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
        .matches("[0-9a-f-]{36}")
        .doesNotContain("unsafe");
  }

  @Test
  void clearsMdcWhenDownstreamChainThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "external-87654321");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain throwingChain =
        (servletRequest, servletResponse) -> {
          assertThat(MDC.get("requestId")).isEqualTo("external-87654321");
          throw new ServletException("downstream failure");
        };

    assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
        .isInstanceOf(ServletException.class)
        .hasMessage("downstream failure");
    assertThat(MDC.get("requestId")).isNull();
  }
}
