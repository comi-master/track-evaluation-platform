package com.example.trackanalysis.evaluation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.trackanalysis.analysis.infrastructure.persistence.AnalysisResultDO;
import com.example.trackanalysis.analysis.infrastructure.persistence.AnalysisResultMapper;
import com.example.trackanalysis.benchmark.infrastructure.persistence.*;
import com.example.trackanalysis.common.exception.*;
import com.example.trackanalysis.evaluation.api.*;
import com.example.trackanalysis.evaluation.infrastructure.persistence.*;
import com.example.trackanalysis.task.application.AnalysisTaskApplicationService;
import com.example.trackanalysis.task.domain.AnalysisTaskStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!no-persistence")
public class EvaluationRunApplicationService {
  private final EvaluationRunMapper runs;
  private final QualityGateMapper gates;
  private final AlgorithmSubmissionMapper submissions;
  private final AlgorithmProjectMapper projects;
  private final EvaluationProtocolMapper protocols;
  private final AnalysisResultMapper results;
  private final AnalysisTaskApplicationService tasks;
  private final ObjectMapper json;

  public EvaluationRunApplicationService(EvaluationRunMapper runs, QualityGateMapper gates,
      AlgorithmSubmissionMapper submissions, AlgorithmProjectMapper projects,
      EvaluationProtocolMapper protocols, AnalysisResultMapper results,
      AnalysisTaskApplicationService tasks, ObjectMapper json) {
    this.runs = runs; this.gates = gates; this.submissions = submissions; this.projects = projects;
    this.protocols = protocols; this.results = results; this.tasks = tasks; this.json = json;
  }

  @Transactional
  public EvaluationRunResponse start(long userId, long submissionId) {
    var submission = ownedSubmission(userId, submissionId);
    var existing = runs.selectBySubmissionId(submissionId);
    if (existing != null) return response(existing);
    var protocol = protocols.selectById(submission.getProtocolId());
    double threshold = number(protocol.getRulesJson(), "defaultThreshold", 1.0);
    EvaluationRunDO run = new EvaluationRunDO();
    run.setSubmissionId(submissionId); run.setStatus("QUEUED"); run.setVersion(0);
    run.setCreatedAt(LocalDateTime.now()); run.setUpdatedAt(LocalDateTime.now());
    if (runs.insertIfAbsent(run) == 0) {
      var concurrent = runs.selectBySubmissionId(submissionId);
      if (concurrent != null) return response(concurrent);
      throw new IllegalStateException("Evaluation conflict could not be resolved");
    }
    var task = tasks.create(userId, submission.getOutputTrackFileId(), threshold);
    runs.attachTask(run.getId(), task.taskId(), "RUNNING");
    submission.setStatus("EVALUATING"); submissions.updateById(submission);
    return response(runs.selectByRunId(run.getId()));
  }

  @Transactional
  public EvaluationRunResponse get(long userId, long runId) {
    var run = runs.selectByRunId(runId);
    if (run == null) throw notFound("Evaluation run");
    ownedSubmission(userId, run.getSubmissionId());
    if (run.getAnalysisTaskId() != null) {
      var task = tasks.get(userId, run.getAnalysisTaskId());
      if (task.status() == AnalysisTaskStatus.SUCCESS && task.analysisResultId() != null) finalizeSuccess(run, task.analysisResultId());
      else if (task.status() == AnalysisTaskStatus.FAILED) finalizeFailure(run, task.safeErrorMessage());
    }
    return response(runs.selectByRunId(runId));
  }

  private void finalizeSuccess(EvaluationRunDO run, long resultId) {
    var result = results.selectById(resultId);
    var submission = submissions.selectBySubmissionId(run.getSubmissionId());
    var protocol = protocols.selectById(submission.getProtocolId());
    JsonNode root;
    try { root = json.readTree(protocol.getRulesJson()); } catch (Exception ex) { finalizeFailure(run, "Invalid protocol rules"); return; }
    JsonNode metricRules = root.path("metrics");
    if (!metricRules.isArray() || metricRules.isEmpty()) { finalizeFailure(run, "Protocol has no metric rules"); return; }
    boolean all = true; var metricJson = json.createObjectNode();
    for (JsonNode rule : metricRules) {
      String code = rule.path("code").asText("").toUpperCase(); String comparison = rule.path("comparison").asText("LTE").toUpperCase();
      double threshold = rule.path("threshold").asDouble(Double.NaN); Double actual = metric(result, code);
      if (actual == null || !Double.isFinite(threshold)) { all = false; continue; }
      boolean passed = compare(actual, threshold, comparison); all &= passed; metricJson.put(code, actual);
      QualityGateDO gate = new QualityGateDO(); gate.setEvaluationRunId(run.getId()); gate.setMetricCode(code);
      gate.setActualValue(actual); gate.setThresholdValue(threshold); gate.setComparison(comparison); gate.setPassed(passed ? 1 : 0);
      gate.setDetail(passed ? "metric passed" : "metric exceeded protocol threshold"); gates.insert(gate);
    }
    runs.finish(run.getId(), resultId, "SUCCESS", all ? "PASS" : "FAIL", metricJson.toString(), null);
  }

  private void finalizeFailure(EvaluationRunDO run, String message) {
    runs.finish(run.getId(), null, "FAILED", "FAIL", "{}", message == null ? "Evaluation failed" : message);
  }

  private AlgorithmSubmissionDO ownedSubmission(long userId, long id) {
    var submission = submissions.selectBySubmissionId(id);
    if (submission == null || projects.selectOwnedById(submission.getProjectId(), userId) == null) throw notFound("Algorithm submission");
    return submission;
  }

  private EvaluationRunResponse response(EvaluationRunDO x) {
    var list = new ArrayList<QualityGateResponse>();
    if (x.getId() != null) for (var g : gates.selectByRunId(x.getId())) list.add(new QualityGateResponse(g.getMetricCode(), g.getActualValue(), g.getThresholdValue(), g.getComparison(), g.getPassed() != null && g.getPassed() == 1, g.getDetail()));
    return new EvaluationRunResponse(x.getId(), x.getSubmissionId(), x.getAnalysisTaskId(), x.getAnalysisResultId(), x.getStatus(), x.getGateStatus(), x.getMetricsJson(), x.getFailureMessage(), list, x.getCreatedAt(), x.getFinishedAt());
  }

  private Double metric(AnalysisResultDO r, String code) {
    return switch (code) { case "RMSE" -> r.getRmse(); case "MEAN_ERROR" -> r.getMeanError(); case "MIN_ERROR" -> r.getMinError(); case "MAX_ERROR" -> r.getMaxError(); case "STANDARD_DEVIATION" -> r.getStandardDeviation(); case "ABNORMAL_RATIO" -> r.getAbnormalRatio(); case "POINT_COUNT" -> r.getPointCount() == null ? null : r.getPointCount().doubleValue(); default -> null; };
  }
  private boolean compare(double actual, double threshold, String op) { return switch (op) { case "GTE" -> actual >= threshold; case "LT" -> actual < threshold; case "GT" -> actual > threshold; case "EQ" -> Double.compare(actual, threshold) == 0; default -> actual <= threshold; }; }
  private double number(String value, String key, double fallback) { try { return json.readTree(value).path(key).asDouble(fallback); } catch (Exception ex) { return fallback; } }
  private BusinessException notFound(String value) { return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, value + " was not found"); }
}
