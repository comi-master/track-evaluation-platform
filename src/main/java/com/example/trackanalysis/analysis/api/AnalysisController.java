package com.example.trackanalysis.analysis.api;

import com.example.trackanalysis.analysis.application.AnalysisApplicationService;
import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1")
public class AnalysisController {
  private final AnalysisApplicationService service;

  public AnalysisController(AnalysisApplicationService service) {
    this.service = service;
  }

  @PostMapping("/track-files/{fileId}/analyses")
  public ResponseEntity<Result<AnalysisResponse>> create(
      @AuthenticationPrincipal AuthenticatedUser p,
      @PathVariable @Min(1) long fileId,
      @Valid @RequestBody CreateAnalysisRequest body,
      HttpServletRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            Result.success(
                service.create(p.id(), fileId, body.abnormalThreshold()),
                RequestIdFilter.requestId(r)));
  }

  @GetMapping("/track-files/{fileId}/analyses/latest")
  public Result<AnalysisResponse> latest(
      @AuthenticationPrincipal AuthenticatedUser p,
      @PathVariable @Min(1) long fileId,
      HttpServletRequest r) {
    return Result.success(service.latest(p.id(), fileId), RequestIdFilter.requestId(r));
  }

  @GetMapping("/track-files/{fileId}/analyses")
  public Result<AnalysisPageResponse> history(
      @AuthenticationPrincipal AuthenticatedUser p,
      @PathVariable @Min(1) long fileId,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      HttpServletRequest r) {
    return Result.success(
        service.history(p.id(), fileId, page, size), RequestIdFilter.requestId(r));
  }

  @GetMapping("/analysis-results/{analysisId}/abnormal-intervals")
  public Result<List<AbnormalIntervalResponse>> intervals(
      @AuthenticationPrincipal AuthenticatedUser p,
      @PathVariable @Min(1) long analysisId,
      HttpServletRequest r) {
    return Result.success(service.intervalList(p.id(), analysisId), RequestIdFilter.requestId(r));
  }

  @GetMapping("/track-files/{fileId}/error-series")
  public Result<ErrorSeriesResponse> errors(
      @AuthenticationPrincipal AuthenticatedUser p,
      @PathVariable @Min(1) long fileId,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "500") @Min(1) @Max(2000) int size,
      HttpServletRequest r) {
    return Result.success(service.errors(p.id(), fileId, page, size), RequestIdFilter.requestId(r));
  }

  @GetMapping("/datasets/{datasetId}/analysis-comparison")
  public Result<List<DatasetAnalysisComparisonResponse>> comparison(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable @Min(1) long datasetId,
      HttpServletRequest request) {
    return Result.success(
        service.comparison(principal.id(), datasetId), RequestIdFilter.requestId(request));
  }
}
