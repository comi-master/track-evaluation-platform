package com.example.trackanalysis.track.api;

import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.example.trackanalysis.track.application.TrackFileApplicationService;
import com.example.trackanalysis.track.domain.ParseStatus;
import com.example.trackanalysis.track.domain.TrackSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/datasets/{datasetId}/track-files")
@SecurityRequirement(name = "bearerAuth")
public class DatasetTrackFileController {

  private final TrackFileApplicationService service;

  public DatasetTrackFileController(TrackFileApplicationService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Upload an owned dataset track CSV")
  public ResponseEntity<Result<TrackFileResponse>> upload(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable @Min(1) long datasetId,
      @RequestPart("file") MultipartFile file,
      @RequestParam TrackSource trackSource,
      HttpServletRequest request) {
    TrackFileResponse response = service.upload(principal.id(), datasetId, file, trackSource);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Result.success(response, RequestIdFilter.requestId(request)));
  }

  @GetMapping
  @Operation(summary = "List owned dataset track files")
  public Result<TrackFilePageResponse> list(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable @Min(1) long datasetId,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) TrackSource trackSource,
      @RequestParam(required = false) ParseStatus parseStatus,
      HttpServletRequest request) {
    return Result.success(
        service.list(principal.id(), datasetId, page, size, trackSource, parseStatus),
        RequestIdFilter.requestId(request));
  }
}
