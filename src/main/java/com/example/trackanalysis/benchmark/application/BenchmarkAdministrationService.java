package com.example.trackanalysis.benchmark.application;

import com.example.trackanalysis.benchmark.api.*;
import com.example.trackanalysis.benchmark.infrastructure.persistence.*;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!no-persistence")
public class BenchmarkAdministrationService {
  private final BenchmarkMapper benchmarks; private final BenchmarkVersionMapper versions; private final EvaluationProtocolMapper protocols;
  public BenchmarkAdministrationService(BenchmarkMapper benchmarks, BenchmarkVersionMapper versions, EvaluationProtocolMapper protocols) { this.benchmarks=benchmarks; this.versions=versions; this.protocols=protocols; }
  @Transactional public long createBenchmark(long userId, CreateBenchmarkRequest r) { var x=new BenchmarkDO(); x.setName(r.name().trim()); x.setDescription(r.description()); x.setVisibility("PUBLIC"); x.setStatus("DRAFT"); x.setCreatedBy(userId); x.setVersion(0); x.setCreatedAt(LocalDateTime.now()); x.setUpdatedAt(LocalDateTime.now()); benchmarks.insert(x); return x.getId(); }
  @Transactional public long createVersion(long userId, long benchmarkId, CreateBenchmarkVersionRequest r) { if (benchmarks.selectById(benchmarkId)==null) throw new IllegalArgumentException("Benchmark not found"); var x=new BenchmarkVersionDO(); x.setBenchmarkId(benchmarkId); x.setVersionNo(r.versionNo()); x.setReferenceTrackFileId(r.referenceTrackFileId()); x.setFormatVersion(r.formatVersion()); x.setDescription(r.description()); x.setStatus("DRAFT"); x.setCreatedBy(userId); x.setCreatedAt(LocalDateTime.now()); versions.insert(x); return x.getId(); }
  @Transactional public long createProtocol(long userId, CreateEvaluationProtocolRequest r) { var x=new EvaluationProtocolDO(); x.setName(r.name().trim()); x.setVersionNo(r.versionNo()); x.setRulesJson(r.rulesJson()); x.setDescription(r.description()); x.setVisibility("PUBLIC"); x.setStatus("DRAFT"); x.setCreatedBy(userId); x.setCreatedAt(LocalDateTime.now()); protocols.insert(x); return x.getId(); }
  @Transactional public void publishBenchmark(long id) { var x=benchmarks.selectById(id); if(x==null) throw new IllegalArgumentException("Benchmark not found"); x.setStatus("PUBLISHED"); x.setUpdatedAt(LocalDateTime.now()); benchmarks.updateById(x); }
  @Transactional public void publishVersion(long id) { var x=versions.selectById(id); if(x==null) throw new IllegalArgumentException("Benchmark version not found"); x.setStatus("PUBLISHED"); x.setPublishedAt(LocalDateTime.now()); versions.updateById(x); }
  @Transactional public void publishProtocol(long id) { var x=protocols.selectById(id); if(x==null) throw new IllegalArgumentException("Protocol not found"); x.setStatus("PUBLISHED"); x.setPublishedAt(LocalDateTime.now()); protocols.updateById(x); }
}
