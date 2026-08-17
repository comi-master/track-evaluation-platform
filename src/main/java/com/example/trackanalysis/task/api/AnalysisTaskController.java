package com.example.trackanalysis.task.api;

import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.example.trackanalysis.task.application.AnalysisTaskApplicationService;
import com.example.trackanalysis.task.application.TaskAuditContext;
import com.example.trackanalysis.task.domain.AnalysisTaskStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1")
public class AnalysisTaskController {
  private final AnalysisTaskApplicationService service;

  public AnalysisTaskController(AnalysisTaskApplicationService service) {
    this.service = service;
  }

  @PostMapping("/track-files/{fileId}/analysis-tasks")
  public ResponseEntity<Result<AnalysisTaskResponse>> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Min(1) long fileId,
      @Valid @RequestBody CreateAnalysisTaskRequest body,
      HttpServletRequest request) {
    return ResponseEntity.accepted()
        .body(
            Result.success(
                service.create(
                    user.id(),
                    fileId,
                    body.abnormalThreshold(),
                    new TaskAuditContext(
                        user.id(),
                        user.username(),
                        RequestIdFilter.requestId(request),
                        request.getRemoteAddr())),
                RequestIdFilter.requestId(request)));
  }

  @GetMapping("/analysis-tasks/{taskId}")
  public Result<AnalysisTaskResponse> get(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Min(1) long taskId,
      HttpServletRequest request) {
    return Result.success(service.get(user.id(), taskId), RequestIdFilter.requestId(request));
  }

  @GetMapping("/track-files/{fileId}/analysis-tasks")
  public Result<AnalysisTaskPageResponse> history(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Min(1) long fileId,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) AnalysisTaskStatus status,
      HttpServletRequest request) {
    return Result.success(
        service.history(user.id(), fileId, page, size, status), RequestIdFilter.requestId(request));
  }

  @PostMapping("/analysis-tasks/{taskId}/retry")
  public ResponseEntity<Result<AnalysisTaskResponse>> retry(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Min(1) long taskId,
      HttpServletRequest request) {
    return ResponseEntity.accepted()
        .body(
            Result.success(
                service.retry(
                    user.id(),
                    taskId,
                    new TaskAuditContext(
                        user.id(),
                        user.username(),
                        RequestIdFilter.requestId(request),
                        request.getRemoteAddr())),
                RequestIdFilter.requestId(request)));
  }
}
