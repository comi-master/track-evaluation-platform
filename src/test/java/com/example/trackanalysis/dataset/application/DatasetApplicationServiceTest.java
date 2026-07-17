package com.example.trackanalysis.dataset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.dataset.api.CreateDatasetRequest;
import com.example.trackanalysis.dataset.api.UpdateDatasetRequest;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetDO;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatasetApplicationServiceTest {

  @Mock private DatasetMapper datasetMapper;

  private DatasetApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new DatasetApplicationService(
            datasetMapper, Clock.fixed(Instant.parse("2026-07-16T08:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void createNormalizesFieldsAndUsesTheAuthenticatedOwner() {
    DatasetDO persisted = dataset(3L, 7L, 0);
    persisted.setName("experiment");
    when(datasetMapper.selectOwnedById(3L, 7L)).thenReturn(persisted);
    when(datasetMapper.insert(any(DatasetDO.class)))
        .thenAnswer(
            invocation -> {
              DatasetDO value = invocation.getArgument(0);
              value.setId(3L);
              value.setVersion(0);
              value.setCreatedAt(LocalDateTime.of(2026, 7, 16, 8, 0));
              value.setUpdatedAt(value.getCreatedAt());
              return 1;
            });

    var response = service.create(7L, new CreateDatasetRequest("  experiment  ", "   "));

    assertThat(response.name()).isEqualTo("experiment");
    assertThat(response.description()).isNull();
    verify(datasetMapper)
        .insert(
            org.mockito.ArgumentMatchers.<DatasetDO>argThat(
                dataset -> dataset.getUserId() == 7L && dataset.getName().equals("experiment")));
  }

  @Test
  void getAlwaysUsesIdAndOwnerTogether() {
    when(datasetMapper.selectOwnedById(20L, 7L)).thenReturn(dataset(20L, 7L, 0));

    assertThat(service.get(7L, 20L).id()).isEqualTo(20L);

    verify(datasetMapper).selectOwnedById(20L, 7L);
  }

  @Test
  void staleOwnedUpdateBecomesConflictButInvisibleResourceBecomesNotFound() {
    UpdateDatasetRequest request = new UpdateDatasetRequest("updated", null, 0);
    when(datasetMapper.updateOwned(
            any(Long.class), any(Long.class), any(), any(), any(Integer.class), any()))
        .thenReturn(0);
    when(datasetMapper.countOwnedActive(20L, 7L)).thenReturn(1);

    assertThatThrownBy(() -> service.update(7L, 20L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

    when(datasetMapper.countOwnedActive(20L, 7L)).thenReturn(0);
    assertThatThrownBy(() -> service.update(7L, 20L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
  }

  @Test
  void deleteFailureDoesNotRevealWhetherAnotherOwnerHasTheId() {
    when(datasetMapper.deleteOwned(any(Long.class), any(Long.class), any(LocalDateTime.class)))
        .thenReturn(0);

    assertThatThrownBy(() -> service.delete(7L, 20L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

    verify(datasetMapper).deleteOwned(any(Long.class), any(Long.class), any(LocalDateTime.class));
  }

  @Test
  void listEscapesSqlWildcardCharactersForLiteralSearch() {
    Page<DatasetDO> result = new Page<>(1, 20, 0);
    result.setRecords(List.of());
    when(datasetMapper.selectOwnedPage(any(), eq(7L), eq("percent!%under!_bang!!")))
        .thenReturn(result);

    service.list(7L, 1, 20, "percent%under_bang!");

    verify(datasetMapper).selectOwnedPage(any(), eq(7L), eq("percent!%under!_bang!!"));
  }

  private DatasetDO dataset(long id, long userId, int version) {
    DatasetDO dataset = new DatasetDO();
    dataset.setId(id);
    dataset.setUserId(userId);
    dataset.setName("dataset");
    dataset.setVersion(version);
    dataset.setCreatedAt(LocalDateTime.of(2026, 7, 16, 8, 0));
    dataset.setUpdatedAt(dataset.getCreatedAt());
    return dataset;
  }
}
