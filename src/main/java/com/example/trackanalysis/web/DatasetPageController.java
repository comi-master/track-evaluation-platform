package com.example.trackanalysis.web;

import com.example.trackanalysis.analysis.api.DatasetAnalysisComparisonResponse;
import com.example.trackanalysis.analysis.application.AnalysisApplicationService;
import com.example.trackanalysis.auth.application.WebIdentityService;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.example.trackanalysis.track.api.TrackPointResponse;
import com.example.trackanalysis.track.application.TrackFileApplicationService;
import com.example.trackanalysis.track.domain.TrackSource;
import com.example.trackanalysis.web.application.AuditContext;
import com.example.trackanalysis.web.application.BusinessScope;
import com.example.trackanalysis.web.application.DatasetWebApplicationService;
import com.example.trackanalysis.web.application.TrajectoryQualityMetricService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@Profile("!no-persistence")
public class DatasetPageController {
  private final WebIdentityService identities;
  private final DatasetWebApplicationService datasets;
  private final TrackFileApplicationService files;
  private final AnalysisApplicationService analyses;
  private final TrajectoryQualityMetricService qualityMetrics;

  public DatasetPageController(
      WebIdentityService identities,
      DatasetWebApplicationService datasets,
      TrackFileApplicationService files,
      AnalysisApplicationService analyses,
      TrajectoryQualityMetricService qualityMetrics) {
    this.identities = identities;
    this.datasets = datasets;
    this.files = files;
    this.analyses = analyses;
    this.qualityMetrics = qualityMetrics;
  }

  @GetMapping("/app/datasets")
  public String retiredList() {
    return "redirect:/app/simulator";
  }

  @GetMapping("/app/history")
  public String history(Principal principal, Model model) {
    var identity = identities.requireActive(principal.getName());
    model.addAttribute("identity", identity);
    var page = datasets.list(BusinessScope.from(identity), 1, 50, "", null, null, null, null);
    var records =
        page.getRecords().stream()
            .map(
                row -> {
                  var sceneFiles =
                      files.list(row.getOwnerId(), row.getId(), 1, 100, null, null).items();
                  long referencePoints =
                      sceneFiles.stream()
                          .filter(file -> file.trackSource() == TrackSource.FUSION)
                          .mapToLong(file -> file.pointCount())
                          .findFirst()
                          .orElse(0L);
                  List<DatasetAnalysisComparisonResponse> results =
                      analyses.comparison(row.getOwnerId(), row.getId()).stream()
                          .filter(result -> result.trackSource() != TrackSource.FUSION)
                          .toList();
                  return new HistoryRecord(
                      row.getId(),
                      row.getName(),
                      row.getDescription(),
                      row.getCreatedAt(),
                      referencePoints,
                      results);
                })
            .toList();
    model.addAttribute("history", records);
    return "app/history";
  }

  public record HistoryRecord(
      long id,
      String name,
      String description,
      java.time.LocalDateTime createdAt,
      long referencePoints,
      List<DatasetAnalysisComparisonResponse> algorithmResults) {}

  @GetMapping("/app/datasets/{id}")
  public String detail(
      Principal principal, @PathVariable long id, HttpServletRequest request, Model model) {
    var identity = identities.requireActive(principal.getName());
    model.addAttribute("identity", identity);
    var dataset = datasets.get(BusinessScope.from(identity), id, context(request));
    model.addAttribute("dataset", dataset);
    var sceneFiles = files.list(dataset.getOwnerId(), id, 1, 100, null, null).items();
    model.addAttribute("files", sceneFiles);
    model.addAttribute("comparison", analyses.comparison(dataset.getOwnerId(), id));
    var metrics = new java.util.HashMap<Long, TrajectoryQualityMetricService.ExtendedMetrics>();
    for (var file : sceneFiles) {
      metrics.put(file.id(), qualityMetrics.metrics(dataset.getOwnerId(), file.id()));
    }
    model.addAttribute("metrics", metrics);
    return "app/dataset-detail";
  }

  @GetMapping("/app/datasets/{id}/plot-data")
  @ResponseBody
  public PlotDataResponse plotData(
      Principal principal, @PathVariable long id, HttpServletRequest request) {
    var identity = identities.requireActive(principal.getName());
    var dataset = datasets.get(BusinessScope.from(identity), id, context(request));
    var series =
        files.list(dataset.getOwnerId(), id, 1, 100, null, null).items().stream()
            .filter(file -> file.parseStatus().name().equals("PARSED"))
            .map(
                file ->
                    new PlotSeries(
                        file.id(),
                        file.originalName(),
                        file.trackSource(),
                        files.points(dataset.getOwnerId(), file.id(), 1, 1200).items()))
            .toList();
    return new PlotDataResponse(id, series);
  }

  public record PlotDataResponse(long datasetId, List<PlotSeries> series) {}

  public record PlotSeries(
      long fileId, String name, TrackSource source, List<TrackPointResponse> points) {}

  @PostMapping(value = "/app/datasets/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public String uploadResult(
      Principal principal,
      @PathVariable long id,
      @RequestParam TrackSource source,
      @RequestParam("file") MultipartFile file,
      @RequestParam(defaultValue = "1.0") double abnormalThreshold,
      HttpServletRequest request,
      RedirectAttributes flash) {
    var identity = identities.requireActive(principal.getName());
    TrackSource resultSource = source == TrackSource.FUSION ? TrackSource.ALGORITHM : source;
    var uploaded = files.upload(identity.id(), id, file, resultSource);
    files.parse(identity.id(), uploaded.id());
    analyses.create(identity.id(), uploaded.id(), abnormalThreshold);
    flash.addFlashAttribute("success", "算法结果已上传并解析，可执行分析后参与比较");
    return "redirect:/app/datasets/" + id;
  }

  @GetMapping("/app/datasets/{id}/download")
  public ResponseEntity<InputStreamResource> download(
      Principal principal, @PathVariable long id, HttpServletRequest request) {
    var value =
        datasets.download(
            BusinessScope.from(identities.requireActive(principal.getName())),
            id,
            context(request));
    String encoded =
        URLEncoder.encode(value.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv"))
        .contentLength(value.size())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=track.csv; filename*=UTF-8''" + encoded)
        .header("X-Content-Type-Options", "nosniff")
        .body(new InputStreamResource(value.content()));
  }

  @PostMapping("/app/datasets/{id}/delete")
  public String delete(
      Principal principal,
      @PathVariable long id,
      HttpServletRequest request,
      RedirectAttributes flash) {
    datasets.delete(
        BusinessScope.from(identities.requireActive(principal.getName())), id, context(request));
    flash.addFlashAttribute("success", "删除请求已接受，文件正在安全清理");
    return "redirect:/app/datasets";
  }

  private AuditContext context(HttpServletRequest request) {
    return new AuditContext(RequestIdFilter.requestId(request), request.getRemoteAddr());
  }
}
