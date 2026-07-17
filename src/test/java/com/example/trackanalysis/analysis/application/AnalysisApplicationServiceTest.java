package com.example.trackanalysis.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.trackanalysis.analysis.infrastructure.persistence.AbnormalIntervalMapper;
import com.example.trackanalysis.analysis.infrastructure.persistence.AnalysisResultDO;
import com.example.trackanalysis.analysis.infrastructure.persistence.AnalysisResultMapper;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.track.domain.ParseStatus;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileDO;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import com.example.trackanalysis.track.infrastructure.persistence.TrackPointDO;
import com.example.trackanalysis.track.infrastructure.persistence.TrackPointMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalysisApplicationServiceTest {
  @Mock TrackFileMapper files;
  @Mock TrackPointMapper points;
  @Mock AnalysisResultMapper results;
  @Mock AbnormalIntervalMapper intervals;
  @Mock TransactionTemplate transactions;
  @Mock DatasetMapper datasets;
  @Mock AnalysisCacheService cache;
  AnalysisApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new AnalysisApplicationService(
            files,
            points,
            results,
            intervals,
            transactions,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
            new AnalysisProperties(2),
            datasets,
            cache);
    when(cache.latest(anyLong(), anyLong())).thenReturn(Optional.empty());
    doAnswer(
            invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null))
        .when(transactions)
        .execute(any());
    doAnswer(
            invocation -> {
              ((AnalysisResultDO) invocation.getArgument(0)).setId(99L);
              return 1;
            })
        .when(results)
        .insert(any(AnalysisResultDO.class));
  }

  @Test
  void calculatesKnownErrorsMeanRmseAndPopulationDeviation() {
    var response = analyze(10, point(1, 1, 0), point(2, 2, 3), point(3, 3, 4));
    assertThat(response.meanError()).isCloseTo(7.0 / 3, within());
    assertThat(response.rmse()).isCloseTo(Math.sqrt(25.0 / 3), within());
    assertThat(response.standardDeviation()).isCloseTo(Math.sqrt(26.0 / 9), within());
    assertThat(response.minError()).isZero();
    assertThat(response.maxError()).isEqualTo(4);
    assertThat(response.maxErrorTime()).isEqualTo(3);
  }

  @Test
  void singleZeroErrorHasZeroDeviationAndNoIntervals() {
    var response = analyze(0, point(1, 1, 0));
    assertThat(response.rmse()).isZero();
    assertThat(response.standardDeviation()).isZero();
    assertThat(response.abnormalCount()).isZero();
    assertThat(response.intervals()).isEmpty();
  }

  @Test
  void thresholdEqualityIsNormalButThresholdZeroDetectsPositiveError() {
    assertThat(analyze(3, point(1, 1, 3)).abnormalCount()).isZero();
    assertThat(analyze(0, point(1, 1, 3)).abnormalCount()).isOne();
  }

  @Test
  void allAbnormalPointsProduceOneClosedFinalIntervalAcrossBatches() {
    var response = analyze(0, point(1, 1, 1), point(2, 2, 2), point(3, 3, 3));
    assertThat(response.abnormalRatio()).isEqualTo(1);
    assertThat(response.intervals())
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i.startSequence()).isEqualTo(1);
              assertThat(i.endSequence()).isEqualTo(3);
              assertThat(i.pointCount()).isEqualTo(3);
            });
  }

  @Test
  void createsMultipleIntervalsAndKeepsEarliestPeakTimeOnTie() {
    var response = analyze(2, point(1, 1, 4), point(2, 2, 0), point(3, 3, 5), point(4, 4, 5));
    assertThat(response.intervals()).hasSize(2);
    assertThat(response.intervals().get(1).peakErrorTime()).isEqualTo(3);
    assertThat(response.maxErrorTime()).isEqualTo(3);
  }

  @Test
  void sequenceGapClosesInterval() {
    var response = analyze(0, point(1, 1, 1), point(3, 2, 1));
    assertThat(response.intervals()).hasSize(2);
  }

  @Test
  void nonIncreasingTimeFailsBeforePersistence() {
    ownedParsed();
    when(points.selectAfterSequence(7, -1, 2)).thenReturn(List.of(point(1, 2, 1), point(2, 2, 1)));
    assertThatThrownBy(() -> service.create(5, 7, 0))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).errorCode())
        .isEqualTo(ErrorCode.CONFLICT);
    verify(results, org.mockito.Mockito.never()).insert(any(AnalysisResultDO.class));
  }

  @Test
  void nonIncreasingSequenceFailsBeforePersistence() {
    ownedParsed();
    when(points.selectAfterSequence(7, -1, 2)).thenReturn(List.of(point(1, 1, 1), point(1, 2, 1)));
    assertThatThrownBy(() -> service.create(5, 7, 0)).isInstanceOf(BusinessException.class);
  }

  @Test
  void nanAndInfinityAreRejected() {
    assertThatThrownBy(() -> analyze(0, point(1, 1, Double.NaN)))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> analyze(0, point(1, 1, Double.POSITIVE_INFINITY)))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void missingOrForeignFileIsNotFoundAndNonParsedConflicts() {
    when(files.selectOwnedById(7, 5)).thenReturn(null);
    assertThatThrownBy(() -> service.create(5, 7, 0))
        .satisfies(
            e ->
                assertThat(((BusinessException) e).errorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    TrackFileDO uploaded = new TrackFileDO();
    uploaded.setParseStatus(ParseStatus.UPLOADED);
    when(files.selectOwnedById(8, 5)).thenReturn(uploaded);
    assertThatThrownBy(() -> service.create(5, 8, 0))
        .satisfies(
            e -> assertThat(((BusinessException) e).errorCode()).isEqualTo(ErrorCode.CONFLICT));
  }

  @Test
  void invalidThresholdsAreRejected() {
    assertThatThrownBy(() -> service.create(5, 7, -1)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.create(5, 7, Double.NaN))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.create(5, 7, Double.POSITIVE_INFINITY))
        .isInstanceOf(BusinessException.class);
  }

  private com.example.trackanalysis.analysis.api.AnalysisResponse analyze(
      double threshold, TrackPointDO... data) {
    ownedParsed();
    AtomicLong after = new AtomicLong(-1);
    when(points.selectAfterSequence(anyLong(), anyLong(), any(Integer.class)))
        .thenAnswer(
            inv -> {
              long cursor = inv.getArgument(1);
              List<TrackPointDO> batch =
                  java.util.Arrays.stream(data)
                      .filter(p -> p.getSequenceNo() > cursor)
                      .limit(2)
                      .toList();
              after.set(cursor);
              return batch;
            });
    return service.create(5, 7, threshold);
  }

  private void ownedParsed() {
    TrackFileDO file = new TrackFileDO();
    file.setId(7L);
    file.setDatasetId(3L);
    file.setParseStatus(ParseStatus.PARSED);
    when(files.selectOwnedById(7, 5)).thenReturn(file);
  }

  private TrackPointDO point(long sequence, double time, double error) {
    TrackPointDO p = new TrackPointDO();
    p.setSequenceNo(sequence);
    p.setTimeValue(time);
    p.setTrueX(0d);
    p.setTrueY(0d);
    p.setTrueZ(0d);
    p.setTrackX(error);
    p.setTrackY(0d);
    p.setTrackZ(0d);
    return p;
  }

  private org.assertj.core.data.Offset<Double> within() {
    return org.assertj.core.data.Offset.offset(1e-12);
  }
}
