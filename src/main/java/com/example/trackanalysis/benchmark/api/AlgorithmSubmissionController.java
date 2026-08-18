package com.example.trackanalysis.benchmark.api;

import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.benchmark.application.AlgorithmSubmissionApplicationService;
import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!no-persistence")
@RequestMapping("/api/v1/algorithm-submissions")
@SecurityRequirement(name = "bearerAuth")
public class AlgorithmSubmissionController {
  private final AlgorithmSubmissionApplicationService service;

  public AlgorithmSubmissionController(AlgorithmSubmissionApplicationService service) {
    this.service = service;
  }

  @PostMapping
  public Result<AlgorithmSubmissionResponse> create(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CreateAlgorithmSubmissionRequest request,
      HttpServletRequest servletRequest) {
    return Result.success(
        service.create(principal.id(), request), RequestIdFilter.requestId(servletRequest));
  }

  @GetMapping("/{id}")
  public Result<AlgorithmSubmissionResponse> get(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable @Min(1) long id,
      HttpServletRequest servletRequest) {
    return Result.success(
        service.get(principal.id(), id), RequestIdFilter.requestId(servletRequest));
  }
}
