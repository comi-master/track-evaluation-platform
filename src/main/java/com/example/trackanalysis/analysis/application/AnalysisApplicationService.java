package com.example.trackanalysis.analysis.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.analysis.api.*;
import com.example.trackanalysis.analysis.infrastructure.persistence.*;
import com.example.trackanalysis.common.exception.*;
import com.example.trackanalysis.common.metrics.AnalysisPerformanceMetrics;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.track.domain.ParseStatus;
import com.example.trackanalysis.track.infrastructure.persistence.*;
import java.time.*;
import java.util.*;
import java.util.function.LongConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AnalysisApplicationService {
  private final TrackFileMapper files;
  private final TrackPointMapper points;
  private final AnalysisResultMapper results;
  private final AbnormalIntervalMapper intervals;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final AnalysisProperties props;
  private final DatasetMapper datasets;
  private final AnalysisCacheService cache;
  private final AnalysisPerformanceMetrics performance;

  public AnalysisApplicationService(
      TrackFileMapper files,
      TrackPointMapper points,
      AnalysisResultMapper results,
      AbnormalIntervalMapper intervals,
      TransactionTemplate tx,
      Clock clock,
      AnalysisProperties props,
      DatasetMapper datasets,
      AnalysisCacheService cache,
      AnalysisPerformanceMetrics performance) {
    this.files = files;
    this.points = points;
    this.results = results;
    this.intervals = intervals;
    this.tx = tx;
    this.clock = clock;
    this.props = props;
    this.datasets = datasets;
    this.cache = cache;
    this.performance = performance;
  }

  public AnalysisResponse create(long user, long file, double threshold) {
    if (!Double.isFinite(threshold) || threshold < 0)
      throw bad("Threshold must be finite and non-negative");
    TrackFileDO f = owned(user, file);
    if (f.getParseStatus() != ParseStatus.PARSED)
      throw new BusinessException(ErrorCode.CONFLICT, "Track file must be parsed");
    AnalysisResponse response = execute(file, threshold, ignored -> {});
    cache.evict(user, file, f.getDatasetId());
    return response;
  }

  public AnalysisResponse createForTask(long file, double threshold, LongConsumer completion) {
    if (!Double.isFinite(threshold) || threshold < 0)
      throw bad("Threshold must be finite and non-negative");
    TrackFileDO trackFile = files.selectById(file);
    if (trackFile == null || trackFile.getParseStatus() != ParseStatus.PARSED)
      throw new BusinessException(ErrorCode.CONFLICT, "Track file must be parsed");
    return execute(file, threshold, completion);
  }

  private AnalysisResponse execute(long file, double threshold, LongConsumer completion) {
    Computed c = compute(file, threshold);
    if (c.count == 0) throw new BusinessException(ErrorCode.CONFLICT, "Track file has no points");
    var writeTimer = performance.start();
    try {
      return tx.execute(
          s -> {
            AnalysisResultDO r = c.result(file, threshold, LocalDateTime.now(clock));
            results.insert(r);
            for (AbnormalIntervalDO i : c.intervals) {
              i.setAnalysisResultId(r.getId());
              i.setCreatedAt(r.getCreatedAt());
            }
            if (!c.intervals.isEmpty()) intervals.batchInsert(c.intervals);
            completion.accept(r.getId());
            return response(r, c.intervals);
          });
    } finally {
      performance.stop(writeTimer, "result.write");
    }
  }

  public AnalysisResponse latest(long user, long file) {
    owned(user, file);
    var cached = cache.latest(user, file);
    if (cached.isPresent()) return cached.get();
    AnalysisResultDO r = results.selectLatestOwned(file, user);
    if (r == null) throw notFound("Analysis result");
    AnalysisResponse response = response(r, intervals.selectOwnedByResult(r.getId(), user));
    cache.putLatest(user, file, response);
    return response;
  }

  public AnalysisResponse get(long user, long resultId) {
    AnalysisResultDO result = results.selectOwnedById(resultId, user);
    if (result == null) throw notFound("Analysis result");
    return response(result, intervals.selectOwnedByResult(resultId, user));
  }

  public AnalysisPageResponse history(long user, long file, int page, int size) {
    owned(user, file);
    var p = results.selectOwnedPage(new Page<>(page, size), file, user);
    return new AnalysisPageResponse(
        p.getCurrent(),
        p.getSize(),
        p.getTotal(),
        p.getPages(),
        p.getRecords().stream().map(r -> response(r, List.of())).toList());
  }

  public List<AbnormalIntervalResponse> intervalList(long user, long id) {
    var list = intervals.selectOwnedByResult(id, user);
    if (list.isEmpty() && results.selectOwnedById(id, user) == null)
      throw notFound("Analysis result");
    return list.stream().map(this::interval).toList();
  }

  public ErrorSeriesResponse errors(long user, long file, int page, int size) {
    owned(user, file);
    var p = points.selectOwnedPage(new Page<>(page, size), file, user);
    return new ErrorSeriesResponse(
        p.getCurrent(),
        p.getSize(),
        p.getTotal(),
        p.getPages(),
        p.getRecords().stream()
            .map(x -> new ErrorPointResponse(x.getSequenceNo(), x.getTimeValue(), error(x)))
            .toList());
  }

  public List<DatasetAnalysisComparisonResponse> comparison(long user, long datasetId) {
    if (datasets.countOwnedActive(datasetId, user) == 0) throw notFound("Dataset");
    var cached = cache.comparison(user, datasetId);
    if (cached.isPresent()) return cached.get();
    var response =
        results.selectLatestByOwnedDataset(datasetId, user).stream()
            .map(
                r -> {
                  TrackFileDO file = files.selectOwnedById(r.getTrackFileId(), user);
                  return new DatasetAnalysisComparisonResponse(
                      r.getTrackFileId(),
                      file.getOriginalName(),
                      file.getTrackSource(),
                      r.getId(),
                      r.getAbnormalThreshold(),
                      r.getPointCount(),
                      r.getMeanError(),
                      r.getRmse(),
                      r.getMinError(),
                      r.getMaxError(),
                      r.getStandardDeviation(),
                      r.getAbnormalCount(),
                      r.getAbnormalRatio(),
                      r.getMaxErrorTime(),
                      r.getCreatedAt());
                })
            .toList();
    cache.putComparison(user, datasetId, response);
    return response;
  }

  public void evictAfterTask(long user, long file, long dataset) {
    cache.evict(user, file, dataset);
  }

  private Computed compute(long file, double threshold) {
    Computed c = new Computed();
    long lastSeq = -1;
    double lastTime = Double.NEGATIVE_INFINITY;
    for (; ; ) {
      var readTimer = performance.start();
      List<TrackPointDO> batch;
      try {
        batch = points.selectAfterSequence(file, lastSeq, props.batchSize());
      } finally {
        performance.stop(readTimer, "points.read");
      }
      if (batch.isEmpty()) break;
      var computeTimer = performance.start();
      try {
        for (var p : batch) {
          if (p.getSequenceNo() <= lastSeq || p.getTimeValue() <= lastTime)
            throw new BusinessException(ErrorCode.CONFLICT, "Track point ordering is invalid");
          double e = error(p);
          c.add(e, p.getTimeValue());
          boolean ab = e > threshold;
          if (ab) {
            if (c.open == null || p.getSequenceNo() != c.open.getEndSequence() + 1) c.close();
            c.open(c, p, e);
          } else c.close();
          lastSeq = p.getSequenceNo();
          lastTime = p.getTimeValue();
        }
      } finally {
        performance.stop(computeTimer, "metrics.compute");
      }
    }
    c.close();
    return c;
  }

  private double error(TrackPointDO p) {
    double x = p.getTrackX() - p.getTrueX(),
        y = p.getTrackY() - p.getTrueY(),
        z = p.getTrackZ() - p.getTrueZ();
    double e = Math.sqrt(x * x + y * y + z * z);
    if (!Double.isFinite(e)) throw bad("Track point contains non-finite values");
    return e;
  }

  private TrackFileDO owned(long u, long f) {
    var x = files.selectOwnedById(f, u);
    if (x == null) throw notFound("Track file");
    return x;
  }

  private BusinessException notFound(String s) {
    return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, s + " was not found");
  }

  private BusinessException bad(String s) {
    return new BusinessException(ErrorCode.INVALID_ARGUMENT, s);
  }

  private AnalysisResponse response(AnalysisResultDO r, List<AbnormalIntervalDO> is) {
    return new AnalysisResponse(
        r.getId(),
        r.getTrackFileId(),
        r.getAbnormalThreshold(),
        r.getPointCount(),
        r.getMeanError(),
        r.getRmse(),
        r.getMinError(),
        r.getMaxError(),
        r.getStandardDeviation(),
        r.getAbnormalCount(),
        r.getAbnormalRatio(),
        r.getMaxErrorTime(),
        r.getCreatedAt(),
        is.stream().map(this::interval).toList());
  }

  private AbnormalIntervalResponse interval(AbnormalIntervalDO i) {
    return new AbnormalIntervalResponse(
        i.getIntervalNo(),
        i.getStartSequence(),
        i.getEndSequence(),
        i.getStartTime(),
        i.getEndTime(),
        i.getPointCount(),
        i.getPeakError(),
        i.getPeakErrorTime());
  }

  static class Computed {
    long count, abnormal;
    double mean, m2, sumSq, min = Double.POSITIVE_INFINITY, max = -1, maxTime;
    List<AbnormalIntervalDO> intervals = new ArrayList<>();
    AbnormalIntervalDO open;

    void add(double e, double t) {
      count++;
      double d = e - mean;
      mean += d / count;
      m2 += d * (e - mean);
      sumSq += e * e;
      if (e < min) min = e;
      if (e > max) {
        max = e;
        maxTime = t;
      }
    }

    void open(Computed c, TrackPointDO p, double e) {
      if (open == null) {
        open = new AbnormalIntervalDO();
        open.setIntervalNo(intervals.size() + 1);
        open.setStartSequence(p.getSequenceNo());
        open.setStartTime(p.getTimeValue());
        open.setPeakError(e);
        open.setPeakErrorTime(p.getTimeValue());
      }
      open.setEndSequence(p.getSequenceNo());
      open.setEndTime(p.getTimeValue());
      open.setPointCount(open.getPointCount() == null ? 1 : open.getPointCount() + 1);
      if (e > open.getPeakError()) {
        open.setPeakError(e);
        open.setPeakErrorTime(p.getTimeValue());
      }
      abnormal++;
    }

    void close() {
      if (open != null) {
        intervals.add(open);
        open = null;
      }
    }

    AnalysisResultDO result(long file, double th, LocalDateTime now) {
      double rmse = Math.sqrt(sumSq / count),
          sd = Math.sqrt(m2 / count),
          ratio = (double) abnormal / count;
      if (!Double.isFinite(mean)
          || !Double.isFinite(rmse)
          || !Double.isFinite(sd)
          || min < 0
          || max < 0)
        throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Analysis values are invalid");
      AnalysisResultDO r = new AnalysisResultDO();
      r.setTrackFileId(file);
      r.setAbnormalThreshold(th);
      r.setPointCount(count);
      r.setMeanError(mean);
      r.setRmse(rmse);
      r.setMinError(min);
      r.setMaxError(max);
      r.setStandardDeviation(sd);
      r.setAbnormalCount(abnormal);
      r.setAbnormalRatio(ratio);
      r.setMaxErrorTime(maxTime);
      r.setCreatedAt(now);
      return r;
    }
  }
}
