package com.example.trackanalysis.track.api;

import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.example.trackanalysis.track.application.TrackFileApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/track-files")
@SecurityRequirement(name = "bearerAuth")
public class TrackFileController {

  private final TrackFileApplicationService service;

  public TrackFileController(TrackFileApplicationService service) {
    this.service = service;
  }

  @GetMapping("/{fileId}")
  @Operation(summary = "Get an owned track file")
  public Result<TrackFileResponse> get(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable @Min(1) long fileId,
      HttpServletRequest request) {
    return Result.success(service.get(principal.id(), fileId), RequestIdFilter.requestId(request));
  }

  @PostMapping("/{fileId}/parse")
  @Operation(summary = "Synchronously parse an owned track file")
  public Result<TrackFileResponse> parse(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable @Min(1) long fileId,
      HttpServletRequest request) {
    return Result.success(
        service.parse(principal.id(), fileId), RequestIdFilter.requestId(request));
  }

  @GetMapping("/{fileId}/points")
  @Operation(summary = "List points from an owned parsed track file")
  public Result<TrackPointPageResponse> points(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable @Min(1) long fileId,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int size,
      HttpServletRequest request) {
    return Result.success(
        service.points(principal.id(), fileId, page, size), RequestIdFilter.requestId(request));
  }
}
