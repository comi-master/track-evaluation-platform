package com.example.trackanalysis.auth.api;

import com.example.trackanalysis.audit.application.SafeAuditService;
import com.example.trackanalysis.auth.application.AuthApplicationService;
import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthApplicationService authService;
  private final SafeAuditService audit;

  public AuthController(AuthApplicationService authService, SafeAuditService audit) {
    this.authService = authService;
    this.audit = audit;
  }

  @PostMapping("/register")
  @Operation(summary = "Register a user")
  public ResponseEntity<Result<UserResponse>> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
    UserResponse user = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Result.success(user, RequestIdFilter.requestId(servletRequest)));
  }

  @PostMapping("/login")
  @Operation(summary = "Log in and issue an access token")
  public Result<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    String requestId = RequestIdFilter.requestId(servletRequest);
    try {
      LoginResponse response = authService.login(request);
      audit.record(
          response.user().id(),
          response.user().username(),
          "REST_LOGIN_SUCCESS",
          "AUTH",
          null,
          requestId,
          servletRequest.getRemoteAddr(),
          null);
      return Result.success(response, requestId);
    } catch (BusinessException exception) {
      String username = request.username() == null ? "anonymous" : request.username().trim();
      if (username.isBlank()) username = "anonymous";
      if (username.length() > 64) username = username.substring(0, 64);
      audit.record(
          null,
          username,
          "REST_LOGIN_FAILURE",
          "AUTH",
          null,
          requestId,
          servletRequest.getRemoteAddr(),
          null);
      throw exception;
    }
  }

  @GetMapping("/me")
  @Operation(summary = "Get the current user", security = @SecurityRequirement(name = "bearerAuth"))
  public Result<UserResponse> currentUser(
      @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest servletRequest) {
    return Result.success(
        authService.currentUser(principal), RequestIdFilter.requestId(servletRequest));
  }

  @PostMapping("/logout")
  @Operation(
      summary = "Invalidate all previously issued access tokens",
      security = @SecurityRequirement(name = "bearerAuth"))
  public Result<Void> logout(
      @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest servletRequest) {
    authService.logout(principal);
    return Result.success(null, RequestIdFilter.requestId(servletRequest));
  }
}
