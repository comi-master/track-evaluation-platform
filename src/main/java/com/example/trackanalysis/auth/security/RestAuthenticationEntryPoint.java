package com.example.trackanalysis.auth.security;

import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authenticationException)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
        response.getOutputStream(),
        Result.failure(
            ErrorCode.UNAUTHORIZED,
            ErrorCode.UNAUTHORIZED.defaultMessage(),
            RequestIdFilter.requestId(request)));
  }
}
