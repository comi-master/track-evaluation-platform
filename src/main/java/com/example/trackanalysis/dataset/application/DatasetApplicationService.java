package com.example.trackanalysis.dataset.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.dataset.api.CreateDatasetRequest;
import com.example.trackanalysis.dataset.api.DatasetPageResponse;
import com.example.trackanalysis.dataset.api.DatasetResponse;
import com.example.trackanalysis.dataset.api.UpdateDatasetRequest;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetDO;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetApplicationService {

  private final DatasetMapper datasetMapper;
  private final Clock clock;

  public DatasetApplicationService(DatasetMapper datasetMapper, Clock clock) {
    this.datasetMapper = datasetMapper;
    this.clock = clock;
  }

  @Transactional
  public DatasetResponse create(long userId, CreateDatasetRequest request) {
    DatasetDO dataset = new DatasetDO();
    dataset.setUserId(userId);
    dataset.setName(normalizeName(request.name()));
    dataset.setDescription(normalizeDescription(request.description()));
    datasetMapper.insert(dataset);
    return toResponse(datasetMapper.selectOwnedById(dataset.getId(), userId));
  }

  @Transactional(readOnly = true)
  public DatasetResponse get(long userId, long datasetId) {
    DatasetDO dataset = datasetMapper.selectOwnedById(datasetId, userId);
    if (dataset == null) {
      throw notFound();
    }
    return toResponse(dataset);
  }

  @Transactional(readOnly = true)
  public DatasetPageResponse list(long userId, int pageNumber, int pageSize, String keyword) {
    String normalizedKeyword = normalizeKeyword(keyword);
    IPage<DatasetDO> page =
        datasetMapper.selectOwnedPage(new Page<>(pageNumber, pageSize), userId, normalizedKeyword);
    List<DatasetResponse> items = page.getRecords().stream().map(this::toResponse).toList();
    return new DatasetPageResponse(
        page.getCurrent(), page.getSize(), page.getTotal(), page.getPages(), items);
  }

  @Transactional
  public DatasetResponse update(long userId, long datasetId, UpdateDatasetRequest request) {
    int changed =
        datasetMapper.updateOwned(
            datasetId,
            userId,
            normalizeName(request.name()),
            normalizeDescription(request.description()),
            request.version(),
            LocalDateTime.now(clock));
    if (changed == 0) {
      if (datasetMapper.countOwnedActive(datasetId, userId) == 0) {
        throw notFound();
      }
      throw new BusinessException(ErrorCode.CONFLICT, "Dataset version is stale");
    }
    return get(userId, datasetId);
  }

  @Transactional
  public void delete(long userId, long datasetId) {
    int changed = datasetMapper.deleteOwned(datasetId, userId, LocalDateTime.now(clock));
    if (changed == 0) {
      throw notFound();
    }
  }

  private String normalizeName(String name) {
    String normalized = name == null ? "" : name.trim();
    if (normalized.isEmpty() || normalized.length() > 128) {
      throw new BusinessException(
          ErrorCode.INVALID_ARGUMENT, "Dataset name must be 1-128 characters after trimming");
    }
    return normalized;
  }

  private String normalizeDescription(String description) {
    if (description == null || description.isBlank()) {
      return null;
    }
    String normalized = description.trim();
    if (normalized.length() > 500) {
      throw new BusinessException(
          ErrorCode.INVALID_ARGUMENT, "Dataset description must not exceed 500 characters");
    }
    return normalized;
  }

  private String normalizeKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    String normalized = keyword.trim();
    if (normalized.length() > 128) {
      throw new BusinessException(
          ErrorCode.INVALID_ARGUMENT, "Dataset keyword must not exceed 128 characters");
    }
    return normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  private DatasetResponse toResponse(DatasetDO dataset) {
    return new DatasetResponse(
        dataset.getId(),
        dataset.getName(),
        dataset.getDescription(),
        dataset.getVersion(),
        dataset.getCreatedAt(),
        dataset.getUpdatedAt());
  }

  private BusinessException notFound() {
    return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Dataset was not found");
  }
}
