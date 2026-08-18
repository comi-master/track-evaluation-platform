package com.example.trackanalysis.benchmark.application;

import com.example.trackanalysis.benchmark.api.AlgorithmProjectResponse;
import com.example.trackanalysis.benchmark.api.CreateAlgorithmProjectRequest;
import com.example.trackanalysis.benchmark.infrastructure.persistence.AlgorithmProjectDO;
import com.example.trackanalysis.benchmark.infrastructure.persistence.AlgorithmProjectMapper;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!no-persistence")
public class AlgorithmProjectApplicationService {
  private final AlgorithmProjectMapper projects;

  public AlgorithmProjectApplicationService(AlgorithmProjectMapper projects) {
    this.projects = projects;
  }

  @Transactional
  public AlgorithmProjectResponse create(long userId, CreateAlgorithmProjectRequest request) {
    AlgorithmProjectDO project = new AlgorithmProjectDO();
    project.setOwnerUserId(userId);
    project.setName(request.name().trim());
    project.setDescription(normalize(request.description()));
    project.setRepositoryUrl(normalize(request.repositoryUrl()));
    project.setVisibility("PRIVATE");
    project.setStatus("ACTIVE");
    projects.insert(project);
    return toResponse(projects.selectOwnedById(project.getId(), userId));
  }

  @Transactional(readOnly = true)
  public AlgorithmProjectResponse get(long userId, long projectId) {
    AlgorithmProjectDO project = projects.selectOwnedById(projectId, userId);
    if (project == null) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Algorithm project was not found");
    }
    return toResponse(project);
  }

  @Transactional(readOnly = true)
  public List<AlgorithmProjectResponse> list(long userId, int limit) {
    return projects.selectOwned(userId, Math.min(limit, 100)).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public AlgorithmProjectResponse updateVisibility(long userId, long projectId, String visibility) {
    var project = projects.selectOwnedById(projectId, userId);
    if (project == null)
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Algorithm project was not found");
    String normalized = visibility == null ? "" : visibility.trim().toUpperCase();
    if (!normalized.equals("PUBLIC") && !normalized.equals("PRIVATE"))
      throw new BusinessException(
          ErrorCode.INVALID_ARGUMENT, "Visibility must be PUBLIC or PRIVATE");
    project.setVisibility(normalized);
    projects.updateById(project);
    return toResponse(projects.selectOwnedById(projectId, userId));
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private AlgorithmProjectResponse toResponse(AlgorithmProjectDO project) {
    return new AlgorithmProjectResponse(
        project.getId(),
        project.getName(),
        project.getDescription(),
        project.getRepositoryUrl(),
        project.getVisibility(),
        project.getStatus(),
        project.getVersion(),
        project.getCreatedAt(),
        project.getUpdatedAt());
  }
}
