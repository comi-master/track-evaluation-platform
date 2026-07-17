package com.example.trackanalysis.report.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetDO;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.report.api.ReportPageResponse;
import com.example.trackanalysis.report.api.ReportResponse;
import com.example.trackanalysis.report.domain.ReportType;
import com.example.trackanalysis.report.infrastructure.persistence.AnalysisReportDO;
import com.example.trackanalysis.report.infrastructure.persistence.AnalysisReportMapper;
import com.example.trackanalysis.report.infrastructure.persistence.ReportSourceRow;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReportApplicationService {
  private final DatasetMapper datasetMapper;
  private final AnalysisReportMapper reportMapper;
  private final ReportHtmlRenderer renderer;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public ReportApplicationService(
      DatasetMapper datasetMapper,
      AnalysisReportMapper reportMapper,
      ReportHtmlRenderer renderer,
      TransactionTemplate transactions,
      Clock clock) {
    this.datasetMapper = datasetMapper;
    this.reportMapper = reportMapper;
    this.renderer = renderer;
    this.transactions = transactions;
    this.clock = clock;
  }

  public ReportResponse create(long userId, long datasetId, String requestedTitle) {
    DatasetDO dataset = ownedDataset(userId, datasetId);
    String title = requestedTitle.trim();
    List<ReportSourceRow> sources = reportMapper.selectLatestSources(datasetId, userId);
    if (sources.isEmpty())
      throw new BusinessException(ErrorCode.CONFLICT, "No analyzed track files are available");
    LocalDateTime now = LocalDateTime.now(clock);
    String html = renderer.render(title, dataset.getName(), now, sources);
    AnalysisReportDO report = new AnalysisReportDO();
    report.setDatasetId(datasetId);
    report.setTitle(title);
    report.setReportType(ReportType.DATASET_COMPARISON);
    report.setSourceFileCount(sources.size());
    report.setContentHtml(html);
    report.setCreatedAt(now);
    transactions.executeWithoutResult(status -> reportMapper.insert(report));
    return response(report);
  }

  public ReportResponse detail(long userId, long reportId) {
    return response(ownedReport(userId, reportId));
  }

  public ReportPageResponse history(long userId, long datasetId, int page, int size) {
    ownedDataset(userId, datasetId);
    Page<AnalysisReportDO> result = new Page<>(page, size);
    reportMapper.selectOwnedPage(result, datasetId, userId);
    return new ReportPageResponse(
        result.getRecords().stream().map(this::response).toList(), result.getTotal(), page, size);
  }

  public ReportContent content(long userId, long reportId) {
    AnalysisReportDO r = ownedReport(userId, reportId);
    return new ReportContent(r.getContentHtml(), "analysis-report-" + r.getId() + ".html");
  }

  private DatasetDO ownedDataset(long userId, long datasetId) {
    DatasetDO d = datasetMapper.selectOwnedById(datasetId, userId);
    if (d == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    return d;
  }

  private AnalysisReportDO ownedReport(long userId, long reportId) {
    AnalysisReportDO r = reportMapper.selectOwnedById(reportId, userId);
    if (r == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    return r;
  }

  private ReportResponse response(AnalysisReportDO r) {
    return new ReportResponse(
        r.getId(),
        r.getDatasetId(),
        r.getTitle(),
        r.getReportType(),
        r.getSourceFileCount(),
        r.getCreatedAt());
  }

  public record ReportContent(String html, String filename) {}
}
