package com.example.trackanalysis.auth.security;

import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public RestAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
        response.getOutputStream(),
        Result.failure(
            ErrorCode.FORBIDDEN,
            ErrorCode.FORBIDDEN.defaultMessage(),
            RequestIdFilter.requestId(request)));
  }
}
