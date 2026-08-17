package com.example.trackanalysis.web.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.audit.application.SafeAuditService;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.dataset.api.CreateDatasetRequest;
import com.example.trackanalysis.dataset.application.DatasetApplicationService;
import com.example.trackanalysis.storage.DatasetDeletionService;
import com.example.trackanalysis.track.application.TrackFileApplicationService;
import com.example.trackanalysis.track.application.TrackFileDownload;
import com.example.trackanalysis.track.domain.TrackSource;
import com.example.trackanalysis.web.infrastructure.BusinessDatasetMapper;
import com.example.trackanalysis.web.infrastructure.BusinessDatasetRow;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("!no-persistence")
public class DatasetWebApplicationService {
  private final DatasetApplicationService datasets;
  private final TrackFileApplicationService files;
  private final BusinessDatasetMapper views;
  private final SafeAuditService audit;
  private final DatasetDeletionService deletion;

  public DatasetWebApplicationService(
      DatasetApplicationService datasets,
      TrackFileApplicationService files,
      BusinessDatasetMapper views,
      SafeAuditService audit,
      DatasetDeletionService deletion) {
    this.datasets = datasets;
    this.files = files;
    this.views = views;
    this.audit = audit;
    this.deletion = deletion;
  }

  public Page<BusinessDatasetRow> list(
      BusinessScope scope,
      int page,
      int size,
      String keyword,
      TrackSource source,
      Long ownerId,
      LocalDate from,
      LocalDate to) {
    String q = normalizeKeyword(keyword);
    return (Page<BusinessDatasetRow>)
        views.selectPage(
            new Page<>(positive(page), Math.min(Math.max(size, 1), 100)),
            scope.actorId(),
            scope.administrator(),
            q,
            source,
            scope.administrator() ? ownerId : null,
            from == null ? null : from.atStartOfDay(),
            to == null ? null : to.plusDays(1).atStartOfDay());
  }

  public BusinessDatasetRow get(BusinessScope scope, long id, AuditContext context) {
    BusinessDatasetRow row = visible(scope, id);
    record(scope, "DATASET_VIEW", id, context, null);
    return row;
  }

  public BusinessDatasetRow resolve(BusinessScope scope, long id) {
    return visible(scope, id);
  }

  public BusinessDatasetRow upload(
      BusinessScope scope,
      String name,
      String description,
      TrackSource source,
      MultipartFile upload,
      AuditContext context) {
    if (source == null)
      throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Track source is required");
    var dataset = datasets.create(scope.actorId(), new CreateDatasetRequest(name, description));
    try {
      var file = files.upload(scope.actorId(), dataset.id(), upload, source);
      files.parse(scope.actorId(), file.id());
      record(scope, "DATASET_UPLOAD", dataset.id(), context, "source=" + source.name());
      return visible(scope, dataset.id());
    } catch (RuntimeException failure) {
      try {
        files.deleteDatasetFiles(dataset.id());
      } catch (RuntimeException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      try {
        datasets.delete(scope.actorId(), dataset.id());
      } catch (RuntimeException ignored) {
        failure.addSuppressed(ignored);
      }
      throw failure;
    }
  }

  public TrackFileDownload download(BusinessScope scope, long id, AuditContext context) {
    BusinessDatasetRow row = visible(scope, id);
    if (!"ACTIVE".equals(row.getDeleteStatus()))
      throw new BusinessException(ErrorCode.CONFLICT, "Dataset deletion is in progress");
    if (row.getFileId() == null)
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Dataset file was not found");
    TrackFileDownload value = files.download(row.getOwnerId(), row.getFileId());
    record(scope, "DATASET_DOWNLOAD", id, context, null);
    return value;
  }

  public void delete(BusinessScope scope, long id, AuditContext context) {
    BusinessDatasetRow row = visible(scope, id);
    deletion.request(
        row.getOwnerId(),
        id,
        scope.actorId(),
        scope.username(),
        context.requestId(),
        context.ipAddress());
  }

  private BusinessDatasetRow visible(BusinessScope scope, long id) {
    BusinessDatasetRow row = views.selectVisible(id, scope.actorId(), scope.administrator());
    if (row == null)
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Dataset was not found");
    return row;
  }

  private void record(BusinessScope s, String action, long id, AuditContext c, String detail) {
    audit.record(
        s.actorId(),
        s.username(),
        action,
        "DATASET",
        String.valueOf(id),
        c.requestId(),
        c.ipAddress(),
        detail);
  }

  private int positive(int page) {
    return Math.max(page, 1);
  }

  private String normalizeKeyword(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String q = raw.trim();
    if (q.length() > 128)
      throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Keyword is too long");
    return q.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}
