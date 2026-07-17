package com.example.trackanalysis.report.api;

import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.example.trackanalysis.report.application.ReportApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports", description = "Immutable dataset comparison HTML reports")
public class ReportController {
  private static final MediaType HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);
  private final ReportApplicationService service;

  public ReportController(ReportApplicationService service) {
    this.service = service;
  }

  @PostMapping("/datasets/{datasetId}/reports")
  @Operation(summary = "Generate an immutable dataset report")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Report created"),
    @ApiResponse(responseCode = "404", description = "Dataset not found or not owned"),
    @ApiResponse(responseCode = "409", description = "No analyzed track file is available")
  })
  public ResponseEntity<Result<ReportResponse>> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Min(1) long datasetId,
      @Valid @RequestBody CreateReportRequest body,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            Result.success(
                service.create(user.id(), datasetId, body.title()),
                RequestIdFilter.requestId(request)));
  }

  @GetMapping("/reports/{reportId}")
  @Operation(summary = "Get owned report metadata")
  @ApiResponse(responseCode = "404", description = "Report not found or not owned")
  public Result<ReportResponse> detail(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Min(1) long reportId,
      HttpServletRequest request) {
    return Result.success(service.detail(user.id(), reportId), RequestIdFilter.requestId(request));
  }

  @GetMapping("/datasets/{datasetId}/reports")
  @Operation(
      summary = "List owned dataset report history",
      description = "Fixed ordering: createdAt descending, then reportId descending")
  @ApiResponse(responseCode = "404", description = "Dataset not found or not owned")
  public Result<ReportPageResponse> history(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Min(1) long datasetId,
      @Parameter(description = "One-based page number", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(1)
          int page,
      @Parameter(description = "Page size, maximum 100", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(100)
          int size,
      HttpServletRequest request) {
    return Result.success(
        service.history(user.id(), datasetId, page, size), RequestIdFilter.requestId(request));
  }

  @GetMapping(value = "/reports/{reportId}/content", produces = "text/html;charset=UTF-8")
  @Operation(summary = "View owned report HTML inline")
  @ApiResponse(responseCode = "404", description = "Report not found or not owned")
  public ResponseEntity<String> content(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Min(1) long reportId) {
    return ResponseEntity.ok()
        .contentType(HTML_UTF8)
        .body(service.content(user.id(), reportId).html());
  }

  @GetMapping(value = "/reports/{reportId}/download", produces = "text/html;charset=UTF-8")
  @Operation(summary = "Download owned report HTML")
  @ApiResponse(responseCode = "404", description = "Report not found or not owned")
  public ResponseEntity<String> download(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Min(1) long reportId) {
    var content = service.content(user.id(), reportId);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(HTML_UTF8);
    headers.setContentDisposition(
        ContentDisposition.attachment()
            .filename(content.filename(), StandardCharsets.UTF_8)
            .build());
    return new ResponseEntity<>(content.html(), headers, HttpStatus.OK);
  }
}
