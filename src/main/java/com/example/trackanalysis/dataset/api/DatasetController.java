package com.example.trackanalysis.dataset.api;

import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.example.trackanalysis.dataset.application.DatasetApplicationService;
import com.example.trackanalysis.storage.DatasetDeletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/datasets")
@SecurityRequirement(name = "bearerAuth")
public class DatasetController {

  private final DatasetApplicationService datasetService;
  private final DatasetDeletionService deletionService;

  public DatasetController(
      DatasetApplicationService datasetService, DatasetDeletionService deletionService) {
    this.datasetService = datasetService;
    this.deletionService = deletionService;
  }

  @PostMapping
  @Operation(summary = "Create a dataset")
  public ResponseEntity<Result<DatasetResponse>> create(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CreateDatasetRequest request,
      HttpServletRequest servletRequest) {
    DatasetResponse dataset = datasetService.create(principal.id(), request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Result.success(dataset, RequestIdFilter.requestId(servletRequest)));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get an owned dataset")
  public Result<DatasetResponse> get(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable @Min(value = 1, message = "id must be positive") long id,
      HttpServletRequest servletRequest) {
    return Result.success(
        datasetService.get(principal.id(), id), RequestIdFilter.requestId(servletRequest));
  }

  @GetMapping
  @Operation(summary = "List owned datasets")
  public Result<DatasetPageResponse> list(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must start at 1") int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "size must be positive")
          @Max(value = 100, message = "size must not exceed 100")
          int size,
      @RequestParam(required = false)
          @Size(max = 128, message = "keyword must not exceed 128 characters")
          String keyword,
      HttpServletRequest servletRequest) {
    return Result.success(
        datasetService.list(principal.id(), page, size, keyword),
        RequestIdFilter.requestId(servletRequest));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update an owned dataset")
  public Result<DatasetResponse> update(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable @Min(value = 1, message = "id must be positive") long id,
      @Valid @RequestBody UpdateDatasetRequest request,
      HttpServletRequest servletRequest) {
    return Result.success(
        datasetService.update(principal.id(), id, request),
        RequestIdFilter.requestId(servletRequest));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Logically delete an owned dataset")
  public Result<Void> delete(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable @Min(value = 1, message = "id must be positive") long id,
      HttpServletRequest servletRequest) {
    deletionService.request(
        principal.id(),
        id,
        principal.id(),
        principal.username(),
        RequestIdFilter.requestId(servletRequest),
        servletRequest.getRemoteAddr());
    return Result.success(null, RequestIdFilter.requestId(servletRequest));
  }
}
