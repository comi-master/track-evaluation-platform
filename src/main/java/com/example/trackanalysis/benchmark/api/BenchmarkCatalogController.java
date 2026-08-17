package com.example.trackanalysis.benchmark.api;

import com.example.trackanalysis.benchmark.infrastructure.persistence.*;
import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!no-persistence")
@RequestMapping("/api/v1/catalog")
public class BenchmarkCatalogController {
  private final BenchmarkMapper benchmarks;
  private final BenchmarkVersionMapper versions;
  private final EvaluationProtocolMapper protocols;
  public BenchmarkCatalogController(BenchmarkMapper benchmarks, BenchmarkVersionMapper versions, EvaluationProtocolMapper protocols) { this.benchmarks = benchmarks; this.versions = versions; this.protocols = protocols; }
  @GetMapping("/benchmarks")
  public Result<List<BenchmarkCatalogResponse>> benchmarks(HttpServletRequest request) {
    var data = benchmarks.selectPublished().stream().map(b -> new BenchmarkCatalogResponse(b.getId(), b.getName(), b.getDescription(), versions.selectPublishedByBenchmark(b.getId()).stream().map(v -> new BenchmarkVersionResponse(v.getId(), v.getVersionNo(), v.getFormatVersion(), v.getDescription())).toList())).toList();
    return Result.success(data, RequestIdFilter.requestId(request));
  }
  @GetMapping("/protocols")
  public Result<List<EvaluationProtocolResponse>> protocols(HttpServletRequest request) {
    var data = protocols.selectPublished().stream().map(p -> new EvaluationProtocolResponse(p.getId(), p.getName(), p.getVersionNo(), p.getDescription(), p.getRulesJson())).toList();
    return Result.success(data, RequestIdFilter.requestId(request));
  }
}
