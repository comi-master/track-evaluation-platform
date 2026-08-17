package com.example.trackanalysis.evaluation.api;

import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!no-persistence")
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class EvaluationRunController {
  private final com.example.trackanalysis.evaluation.application.EvaluationRunApplicationService service;
  public EvaluationRunController(com.example.trackanalysis.evaluation.application.EvaluationRunApplicationService service) { this.service = service; }
  @PostMapping("/algorithm-submissions/{submissionId}/evaluate")
  public Result<EvaluationRunResponse> start(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Min(1) long submissionId, HttpServletRequest request) { return Result.success(service.start(user.id(), submissionId), RequestIdFilter.requestId(request)); }
  @GetMapping("/evaluations/{runId}")
  public Result<EvaluationRunResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Min(1) long runId, HttpServletRequest request) { return Result.success(service.get(user.id(), runId), RequestIdFilter.requestId(request)); }
  @GetMapping("/evaluations/{runId}/gate")
  public Result<CiGateResponse> gate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Min(1) long runId, HttpServletRequest request) {
    var result = service.get(user.id(), runId);
    return Result.success(new CiGateResponse(result.id(), result.status(), result.gateStatus(), "PASS".equals(result.gateStatus()), result.failureMessage()), RequestIdFilter.requestId(request));
  }
}
