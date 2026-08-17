package com.example.trackanalysis.auth.security;

import com.example.trackanalysis.audit.application.SafeAuditService;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class WebAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {
  private final SafeAuditService audit;

  public WebAuthenticationFailureHandler(SafeAuditService audit) {
    super("/login?error");
    this.audit = audit;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    String username = request.getParameter("username");
    String snapshot = username == null || username.isBlank() ? "anonymous" : username.trim();
    if (snapshot.length() > 64) {
      snapshot = snapshot.substring(0, 64);
    }
    audit.record(
        null,
        snapshot,
        "WEB_LOGIN_FAILURE",
        "AUTH",
        null,
        RequestIdFilter.requestId(request),
        request.getRemoteAddr(),
        null);
    super.onAuthenticationFailure(request, response, exception);
  }
}
