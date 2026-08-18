package com.example.trackanalysis.benchmark.api;

import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.benchmark.application.AlgorithmProjectApplicationService;
import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/algorithm-projects")
@SecurityRequirement(name = "bearerAuth")
@Profile("!no-persistence")
public class AlgorithmProjectController {
  private final AlgorithmProjectApplicationService service;

  public AlgorithmProjectController(AlgorithmProjectApplicationService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(summary = "Create an algorithm project")
  public Result<AlgorithmProjectResponse> create(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CreateAlgorithmProjectRequest request,
      HttpServletRequest servletRequest) {
    return Result.success(
        service.create(principal.id(), request), RequestIdFilter.requestId(servletRequest));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get an owned algorithm project")
  public Result<AlgorithmProjectResponse> get(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable @Min(1) long id,
      HttpServletRequest servletRequest) {
    return Result.success(
        service.get(principal.id(), id), RequestIdFilter.requestId(servletRequest));
  }

  @GetMapping
  @Operation(summary = "List owned algorithm projects")
  public Result<List<AlgorithmProjectResponse>> list(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      HttpServletRequest servletRequest) {
    return Result.success(
        service.list(principal.id(), limit), RequestIdFilter.requestId(servletRequest));
  }

  @PutMapping("/{id}/visibility")
  public Result<AlgorithmProjectResponse> updateVisibility(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable @Min(1) long id,
      @Valid @RequestBody UpdateAlgorithmProjectVisibilityRequest request,
      HttpServletRequest servletRequest) {
    return Result.success(
        service.updateVisibility(principal.id(), id, request.visibility()),
        RequestIdFilter.requestId(servletRequest));
  }
}
