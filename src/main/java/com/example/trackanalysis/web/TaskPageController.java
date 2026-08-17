package com.example.trackanalysis.web;

import com.example.trackanalysis.analysis.application.AnalysisApplicationService;
import com.example.trackanalysis.audit.application.SafeAuditService;
import com.example.trackanalysis.auth.application.WebIdentityService;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.example.trackanalysis.task.api.AnalysisTaskResponse;
import com.example.trackanalysis.task.application.AnalysisTaskApplicationService;
import com.example.trackanalysis.task.application.TaskAuditContext;
import com.example.trackanalysis.task.domain.AnalysisTaskStatus;
import com.example.trackanalysis.track.application.TrackFileApplicationService;
import com.example.trackanalysis.web.application.BusinessScope;
import com.example.trackanalysis.web.application.DatasetWebApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Profile("!no-persistence")
@RequestMapping("/app")
public class TaskPageController {
  private final WebIdentityService identities;
  private final AnalysisTaskApplicationService tasks;
  private final AnalysisApplicationService analyses;
  private final DatasetWebApplicationService datasets;
  private final SafeAuditService audit;
  private final TrackFileApplicationService files;

  public TaskPageController(
      WebIdentityService identities,
      AnalysisTaskApplicationService tasks,
      AnalysisApplicationService analyses,
      DatasetWebApplicationService datasets,
      SafeAuditService audit,
      TrackFileApplicationService files) {
    this.identities = identities;
    this.tasks = tasks;
    this.analyses = analyses;
    this.datasets = datasets;
    this.audit = audit;
    this.files = files;
  }

  @GetMapping("/tasks")
  public String list(
      Principal principal,
      @RequestParam(required = false) Long fileId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(required = false) AnalysisTaskStatus status,
      @RequestParam(required = false) Long ownerId,
      HttpServletRequest request,
      Model model) {
    var identity = identities.requireActive(principal.getName());
    model.addAttribute("identity", identity);
    model.addAttribute("fileId", fileId);
    model.addAttribute("ownerId", ownerId);
    model.addAttribute("status", status);
    model.addAttribute(
        "page",
        tasks.historyVisible(
            identity.id(), identity.roles().contains("ADMIN"), fileId, ownerId, page, 20, status));
    return "app/tasks";
  }

  @PostMapping("/datasets/{datasetId}/tasks")
  public String createForDataset(
      Principal principal,
      @PathVariable long datasetId,
      @RequestParam(defaultValue = "10") double threshold,
      HttpServletRequest request) {
    var identity = identities.requireActive(principal.getName());
    var row = datasets.resolve(BusinessScope.from(identity), datasetId);
    if (row.getFileId() == null)
      throw new com.example.trackanalysis.common.exception.BusinessException(
          com.example.trackanalysis.common.exception.ErrorCode.CONFLICT,
          "Dataset has no parsed file");
    var task =
        tasks.create(row.getOwnerId(), row.getFileId(), threshold, auditContext(identity, request));
    return "redirect:/app/tasks/" + task.taskId();
  }

  @PostMapping("/tasks")
  public String create(
      Principal principal,
      @RequestParam long fileId,
      @RequestParam(defaultValue = "10") double threshold,
      HttpServletRequest request) {
    var identity = identities.requireActive(principal.getName());
    var task =
        tasks.create(
            ownerForFile(BusinessScope.from(identity), fileId),
            fileId,
            threshold,
            auditContext(identity, request));
    return "redirect:/app/tasks/" + task.taskId();
  }

  @GetMapping("/tasks/{id}")
  public String detail(
      Principal principal, @PathVariable long id, HttpServletRequest request, Model model) {
    var identity = identities.requireActive(principal.getName());
    AnalysisTaskResponse task = visibleTask(BusinessScope.from(identity), id);
    model.addAttribute("identity", identity);
    model.addAttribute("task", task);
    record(identity.id(), identity.username(), "TASK_VIEW", id, request, null);
    return "app/task-detail";
  }

  @GetMapping("/tasks/{id}/status")
  @ResponseBody
  public AnalysisTaskResponse status(Principal principal, @PathVariable long id) {
    return visibleTask(BusinessScope.from(identities.requireActive(principal.getName())), id);
  }

  @PostMapping("/tasks/{id}/retry")
  public String retry(Principal principal, @PathVariable long id, HttpServletRequest request) {
    var identity = identities.requireActive(principal.getName());
    AnalysisTaskResponse current = visibleTask(BusinessScope.from(identity), id);
    tasks.retry(
        ownerForFile(BusinessScope.from(identity), current.fileId()),
        id,
        auditContext(identity, request));
    return "redirect:/app/tasks/" + id;
  }

  @PostMapping("/tasks/{id}/cancel")
  public String cancel(Principal principal, @PathVariable long id, HttpServletRequest request) {
    var identity = identities.requireActive(principal.getName());
    AnalysisTaskResponse current = visibleTask(BusinessScope.from(identity), id);
    tasks.cancel(
        ownerForFile(BusinessScope.from(identity), current.fileId()),
        id,
        auditContext(identity, request));
    return "redirect:/app/tasks/" + id;
  }

  @GetMapping("/tasks/{id}/result")
  public String result(
      Principal principal, @PathVariable long id, HttpServletRequest request, Model model) {
    var identity = identities.requireActive(principal.getName());
    AnalysisTaskResponse task = visibleTask(BusinessScope.from(identity), id);
    requireSuccessfulResult(task);
    long owner = ownerForFile(BusinessScope.from(identity), task.fileId());
    model.addAttribute("identity", identity);
    model.addAttribute("task", task);
    model.addAttribute("result", analyses.get(owner, task.analysisResultId()));
    record(
        identity.id(), identity.username(), "RESULT_VIEW", task.analysisResultId(), request, null);
    return "app/result";
  }

  @GetMapping("/tasks/{id}/result.csv")
  public ResponseEntity<byte[]> resultDownload(
      Principal principal, @PathVariable long id, HttpServletRequest request) {
    var identity = identities.requireActive(principal.getName());
    AnalysisTaskResponse task = visibleTask(BusinessScope.from(identity), id);
    requireSuccessfulResult(task);
    var r =
        analyses.get(
            ownerForFile(BusinessScope.from(identity), task.fileId()), task.analysisResultId());
    String csv =
        "metric,value\npoint_count,"
            + r.pointCount()
            + "\nmean_error,"
            + r.meanError()
            + "\nrmse,"
            + r.rmse()
            + "\nmin_error,"
            + r.minError()
            + "\nmax_error,"
            + r.maxError()
            + "\nstandard_deviation,"
            + r.standardDeviation()
            + "\nabnormal_count,"
            + r.abnormalCount()
            + "\nabnormal_ratio,"
            + r.abnormalRatio()
            + "\n";
    record(
        identity.id(),
        identity.username(),
        "RESULT_DOWNLOAD",
        task.analysisResultId(),
        request,
        null);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=analysis-result-" + id + ".csv")
        .header("X-Content-Type-Options", "nosniff")
        .body(csv.getBytes(StandardCharsets.UTF_8));
  }

  private AnalysisTaskResponse visibleTask(BusinessScope scope, long id) {
    return tasks.getVisible(scope.actorId(), scope.administrator(), id);
  }

  private void requireSuccessfulResult(AnalysisTaskResponse task) {
    if (task.status() != AnalysisTaskStatus.SUCCESS || task.analysisResultId() == null) {
      throw new com.example.trackanalysis.common.exception.BusinessException(
          com.example.trackanalysis.common.exception.ErrorCode.CONFLICT,
          "The task does not have a successful result");
    }
  }

  private long ownerForFile(BusinessScope scope, long fileId) {
    return files.visibleOwnerId(scope.actorId(), scope.administrator(), fileId);
  }

  private void record(
      Long uid, String username, String action, Long id, HttpServletRequest req, String detail) {
    audit.record(
        uid,
        username,
        action,
        action.startsWith("RESULT") ? "RESULT" : "TASK",
        String.valueOf(id),
        RequestIdFilter.requestId(req),
        req.getRemoteAddr(),
        detail);
  }

  private TaskAuditContext auditContext(
      com.example.trackanalysis.auth.application.WebIdentityService.WebIdentity identity,
      HttpServletRequest request) {
    return new TaskAuditContext(
        identity.id(),
        identity.username(),
        RequestIdFilter.requestId(request),
        request.getRemoteAddr());
  }
}
