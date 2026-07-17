package com.example.trackanalysis.track.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.storage.ObjectStorageService;
import com.example.trackanalysis.track.domain.TrackSource;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileDO;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import com.example.trackanalysis.track.infrastructure.persistence.TrackPointMapper;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class TrackFileApplicationServiceTest {

  @Mock DatasetMapper datasetMapper;
  @Mock TrackFileMapper fileMapper;
  @Mock TrackPointMapper pointMapper;
  @Mock ObjectStorageService storage;
  @Mock CsvTrackParser parser;
  @Mock TransactionTemplate transactions;
  TrackFileApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new TrackFileApplicationService(
            datasetMapper,
            fileMapper,
            pointMapper,
            storage,
            parser,
            new TrackFileProperties(1024, 100, 10),
            transactions,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void uploadsStreamedContentWithHashSafeNameAndOpaqueObjectKey() throws Exception {
    byte[] content =
        "time,true_x,true_y,true_z,track_x,track_y,track_z\n1,2,3,4,5,6,7\n".getBytes();
    MockMultipartFile upload = new MockMultipartFile("file", "../unsafe.csv", "text/csv", content);
    AtomicReference<String> objectName = new AtomicReference<>();
    AtomicReference<byte[]> stored = new AtomicReference<>();
    when(datasetMapper.countOwnedActive(9, 7)).thenReturn(1);
    doAnswer(
            invocation -> {
              objectName.set(invocation.getArgument(0));
              stored.set(((InputStream) invocation.getArgument(1)).readAllBytes());
              return null;
            })
        .when(storage)
        .put(any(), any(), anyLong(), eq("text/csv"));
    doAnswer(
            invocation -> {
              TrackFileDO file = invocation.getArgument(0);
              file.setId(11L);
              return 1;
            })
        .when(fileMapper)
        .insertOwned(any(TrackFileDO.class), eq(7L));
    var response = service.upload(7, 9, upload, TrackSource.RADAR);

    assertThat(response.originalName()).isEqualTo("unsafe.csv");
    assertThat(stored.get()).isEqualTo(content);
    assertThat(objectName.get()).matches("7/9/[0-9a-f-]{36}\\.csv");
    assertThat(objectName.get()).doesNotContain("unsafe").doesNotContain("..");
  }

  @Test
  void rejectsForeignDatasetEmptyWrongExtensionAndOversizeBeforeStorage() {
    when(datasetMapper.countOwnedActive(9, 7)).thenReturn(0);
    assertThatThrownBy(() -> service.upload(7, 9, csv("a.csv", "x"), TrackSource.OTHER))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Dataset was not found");

    when(datasetMapper.countOwnedActive(9, 7)).thenReturn(1);
    assertThatThrownBy(() -> service.upload(7, 9, csv("a.txt", "x"), TrackSource.OTHER))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(".csv");
    assertThatThrownBy(() -> service.upload(7, 9, csv("a.CSV", ""), TrackSource.OTHER))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("must not be empty");
    assertThatThrownBy(
            () -> service.upload(7, 9, csv("a.csv", "x".repeat(1025)), TrackSource.OTHER))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("size limit");
    verify(storage, never()).put(any(), any(), anyLong(), any());
  }

  @Test
  void compensatesObjectWhenDatabaseRejectsDuplicateHash() {
    when(datasetMapper.countOwnedActive(9, 7)).thenReturn(1);
    when(fileMapper.insertOwned(any(TrackFileDO.class), eq(7L)))
        .thenThrow(new DuplicateKeyException("duplicate"));
    AtomicReference<String> key = new AtomicReference<>();
    doAnswer(
            invocation -> {
              key.set(invocation.getArgument(0));
              return null;
            })
        .when(storage)
        .put(any(), any(), anyLong(), any());

    assertThatThrownBy(() -> service.upload(7, 9, csv("a.csv", "content"), TrackSource.OTHER))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Identical file content");

    verify(storage).deleteBestEffort(key.get());
  }

  @Test
  void compensatesObjectWhenDatabaseFailsForReasonsOtherThanDuplicate() {
    when(datasetMapper.countOwnedActive(9, 7)).thenReturn(1);
    when(fileMapper.insertOwned(any(TrackFileDO.class), eq(7L)))
        .thenThrow(new IllegalStateException("database unavailable"));
    AtomicReference<String> key = new AtomicReference<>();
    doAnswer(
            invocation -> {
              key.set(invocation.getArgument(0));
              return null;
            })
        .when(storage)
        .put(any(), any(), anyLong(), any());

    assertThatThrownBy(() -> service.upload(7, 9, csv("a.csv", "content"), TrackSource.OTHER))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("metadata storage");

    verify(storage).deleteBestEffort(key.get());
  }

  private MockMultipartFile csv(String name, String content) {
    return new MockMultipartFile("file", name, "text/csv", content.getBytes());
  }
}
