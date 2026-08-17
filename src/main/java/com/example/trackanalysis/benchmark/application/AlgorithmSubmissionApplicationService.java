package com.example.trackanalysis.benchmark.application;

import com.example.trackanalysis.benchmark.api.*;
import com.example.trackanalysis.benchmark.infrastructure.persistence.*;
import com.example.trackanalysis.common.exception.*;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!no-persistence")
public class AlgorithmSubmissionApplicationService {
  private final AlgorithmSubmissionMapper submissions;
  private final AlgorithmProjectMapper projects;
  private final BenchmarkVersionMapper benchmarks;
  private final EvaluationProtocolMapper protocols;
  private final TrackFileMapper files;

  public AlgorithmSubmissionApplicationService(
      AlgorithmSubmissionMapper submissions,
      AlgorithmProjectMapper projects,
      BenchmarkVersionMapper benchmarks,
      EvaluationProtocolMapper protocols,
      TrackFileMapper files) {
    this.submissions = submissions;
    this.projects = projects;
    this.benchmarks = benchmarks;
    this.protocols = protocols;
    this.files = files;
  }

  @Transactional
  public AlgorithmSubmissionResponse create(long userId, CreateAlgorithmSubmissionRequest request) {
    if (projects.selectOwnedById(request.projectId(), userId) == null)
      throw notFound("Algorithm project");
    var benchmark = benchmarks.selectById(request.benchmarkVersionId());
    if (benchmark == null || !"PUBLISHED".equals(benchmark.getStatus()))
      throw new BusinessException(ErrorCode.CONFLICT, "Benchmark version is not published");
    var protocol = protocols.selectById(request.protocolId());
    if (protocol == null || !"PUBLISHED".equals(protocol.getStatus()))
      throw new BusinessException(ErrorCode.CONFLICT, "Evaluation protocol is not published");
    if (files.selectOwnedById(request.outputTrackFileId(), userId) == null)
      throw notFound("Output track file");
    String key = digest(request.projectId() + ":" + request.benchmarkVersionId() + ":" + request.protocolId()
        + ":" + request.outputTrackFileId() + ":" + request.algorithmVersion().trim());
    AlgorithmSubmissionDO submission = new AlgorithmSubmissionDO();
    submission.setProjectId(request.projectId());
    submission.setBenchmarkVersionId(request.benchmarkVersionId());
    submission.setProtocolId(request.protocolId());
    submission.setOutputTrackFileId(request.outputTrackFileId());
    submission.setAlgorithmVersion(request.algorithmVersion().trim());
    submission.setGitCommit(request.gitCommit());
    submission.setSubmissionKey(key);
    submission.setStatus("SUBMITTED");
    submission.setDescription(request.description());
    if (submissions.insertIfAbsent(submission) == 0) {
      var existing = submissions.selectByProjectAndKey(request.projectId(), key);
      if (existing != null) return response(existing);
      throw new IllegalStateException("Submission conflict could not be resolved");
    }
    return response(submission);
  }

  @Transactional(readOnly = true)
  public AlgorithmSubmissionResponse get(long userId, long id) {
    var submission = submissions.selectBySubmissionId(id);
    if (submission == null || projects.selectOwnedById(submission.getProjectId(), userId) == null)
      throw notFound("Algorithm submission");
    return response(submission);
  }

  private AlgorithmSubmissionResponse response(AlgorithmSubmissionDO x) {
    return new AlgorithmSubmissionResponse(x.getId(), x.getProjectId(), x.getBenchmarkVersionId(),
        x.getProtocolId(), x.getOutputTrackFileId(), x.getAlgorithmVersion(), x.getGitCommit(),
        x.getStatus(), x.getCreatedAt());
  }

  private BusinessException notFound(String value) {
    return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, value + " was not found");
  }

  private String digest(String value) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder(64);
      for (byte b : bytes) out.append(String.format("%02x", b));
      return out.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
