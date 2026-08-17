package com.example.trackanalysis.benchmark.api;

import com.example.trackanalysis.auth.security.AuthenticatedUser;
import com.example.trackanalysis.benchmark.application.BenchmarkAdministrationService;
import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!no-persistence")
@RequestMapping("/api/v1/admin")
public class BenchmarkAdministrationController {
  private final BenchmarkAdministrationService service;
  public BenchmarkAdministrationController(BenchmarkAdministrationService service) { this.service=service; }
  @PostMapping("/benchmarks") public Result<Long> createBenchmark(@AuthenticationPrincipal AuthenticatedUser u, Authentication a, @Valid @RequestBody CreateBenchmarkRequest r, HttpServletRequest q) { admin(a); return Result.success(service.createBenchmark(u.id(),r), RequestIdFilter.requestId(q)); }
  @PostMapping("/benchmarks/{id}/versions") public Result<Long> createVersion(@AuthenticationPrincipal AuthenticatedUser u, Authentication a, @PathVariable @Min(1) long id, @Valid @RequestBody CreateBenchmarkVersionRequest r, HttpServletRequest q) { admin(a); return Result.success(service.createVersion(u.id(),id,r), RequestIdFilter.requestId(q)); }
  @PostMapping("/protocols") public Result<Long> createProtocol(@AuthenticationPrincipal AuthenticatedUser u, Authentication a, @Valid @RequestBody CreateEvaluationProtocolRequest r, HttpServletRequest q) { admin(a); return Result.success(service.createProtocol(u.id(),r), RequestIdFilter.requestId(q)); }
  @PostMapping("/benchmarks/{id}/publish") public Result<Void> publishBenchmark(Authentication a, @PathVariable @Min(1) long id, HttpServletRequest q) { admin(a); service.publishBenchmark(id); return Result.success(null, RequestIdFilter.requestId(q)); }
  @PostMapping("/benchmark-versions/{id}/publish") public Result<Void> publishVersion(Authentication a, @PathVariable @Min(1) long id, HttpServletRequest q) { admin(a); service.publishVersion(id); return Result.success(null, RequestIdFilter.requestId(q)); }
  @PostMapping("/protocols/{id}/publish") public Result<Void> publishProtocol(Authentication a, @PathVariable @Min(1) long id, HttpServletRequest q) { admin(a); service.publishProtocol(id); return Result.success(null, RequestIdFilter.requestId(q)); }
  private void admin(Authentication a) { if (a==null || a.getAuthorities().stream().noneMatch(x -> "ROLE_ADMIN".equals(x.getAuthority()))) throw new AccessDeniedException("Administrator role required"); }
}
