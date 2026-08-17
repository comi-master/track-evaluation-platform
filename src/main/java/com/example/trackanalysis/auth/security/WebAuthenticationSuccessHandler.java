package com.example.trackanalysis.auth.security;

import com.example.trackanalysis.audit.application.SafeAuditService;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.example.trackanalysis.user.infrastructure.persistence.SysUserMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class WebAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
  private final SysUserMapper users;
  private final SafeAuditService audit;
  private final Clock clock;

  public WebAuthenticationSuccessHandler(SysUserMapper users, SafeAuditService audit, Clock clock) {
    this.users = users;
    this.audit = audit;
    this.clock = clock;
    setDefaultTargetUrl("/app/simulator");
    setAlwaysUseDefaultTargetUrl(true);
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    var user = users.selectActiveByUsername(authentication.getName());
    if (user != null) {
      users.recordSuccessfulLogin(user.getId(), LocalDateTime.now(clock));
      audit.record(
          user.getId(),
          user.getUsername(),
          "WEB_LOGIN_SUCCESS",
          "AUTH",
          null,
          RequestIdFilter.requestId(request),
          request.getRemoteAddr(),
          null);
    }
    super.onAuthenticationSuccess(request, response, authentication);
  }
}
