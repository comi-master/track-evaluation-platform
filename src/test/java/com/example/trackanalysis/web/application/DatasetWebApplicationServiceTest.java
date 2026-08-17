package com.example.trackanalysis.web.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.trackanalysis.audit.application.SafeAuditService;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.dataset.api.DatasetResponse;
import com.example.trackanalysis.dataset.application.DatasetApplicationService;
import com.example.trackanalysis.storage.DatasetDeletionService;
import com.example.trackanalysis.track.api.TrackFileResponse;
import com.example.trackanalysis.track.application.TrackFileApplicationService;
import com.example.trackanalysis.track.domain.ParseStatus;
import com.example.trackanalysis.track.domain.TrackSource;
import com.example.trackanalysis.web.infrastructure.BusinessDatasetMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DatasetWebApplicationServiceTest {
  DatasetApplicationService datasets = mock(DatasetApplicationService.class);
  TrackFileApplicationService files = mock(TrackFileApplicationService.class);
  BusinessDatasetMapper views = mock(BusinessDatasetMapper.class);
  SafeAuditService audit = mock(SafeAuditService.class);
  DatasetDeletionService deletion = mock(DatasetDeletionService.class);
  DatasetWebApplicationService service =
      new DatasetWebApplicationService(datasets, files, views, audit, deletion);

  @Test
  void parseFailureDeletesUploadedObjectBeforeLogicallyDeletingDataset() {
    LocalDateTime now = LocalDateTime.parse("2026-01-01T00:00:00");
    when(datasets.create(eq(7L), any()))
        .thenReturn(new DatasetResponse(9, "dataset", null, 0, now, now));
    when(files.upload(eq(7L), eq(9L), any(), eq(TrackSource.FUSION)))
        .thenReturn(
            new TrackFileResponse(
                11,
                9,
                "track.csv",
                "a".repeat(64),
                10,
                TrackSource.FUSION,
                ParseStatus.UPLOADED,
                0,
                null,
                now));
    when(files.parse(7, 11))
        .thenThrow(new BusinessException(ErrorCode.FILE_FORMAT_ERROR, "Invalid CSV"));

    assertThatThrownBy(
            () ->
                service.upload(
                    new BusinessScope(7, "researcher", false),
                    "dataset",
                    null,
                    TrackSource.FUSION,
                    new MockMultipartFile("file", "track.csv", "text/csv", new byte[] {1}),
                    new AuditContext("request", "127.0.0.1")))
        .isInstanceOf(BusinessException.class);

    var order = inOrder(files, datasets);
    order.verify(files).deleteDatasetFiles(9);
    order.verify(datasets).delete(7, 9);
    verifyNoInteractions(audit);
  }
}
