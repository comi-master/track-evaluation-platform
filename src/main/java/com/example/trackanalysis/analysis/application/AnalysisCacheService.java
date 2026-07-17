package com.example.trackanalysis.analysis.application;

import com.example.trackanalysis.analysis.api.AnalysisResponse;
import com.example.trackanalysis.analysis.api.DatasetAnalysisComparisonResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnalysisCacheService {
  private static final Logger log = LoggerFactory.getLogger(AnalysisCacheService.class);
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final AnalysisCacheProperties properties;

  public AnalysisCacheService(
      StringRedisTemplate redis, ObjectMapper json, AnalysisCacheProperties properties) {
    this.redis = redis;
    this.json = json;
    this.properties = properties;
  }

  public Optional<AnalysisResponse> latest(long user, long file) {
    return read(latestKey(user, file), AnalysisResponse.class);
  }

  public void putLatest(long user, long file, AnalysisResponse value) {
    write(latestKey(user, file), value, properties.latestTtl());
  }

  public Optional<List<DatasetAnalysisComparisonResponse>> comparison(long user, long dataset) {
    try {
      String value = redis.opsForValue().get(comparisonKey(user, dataset));
      return value == null
          ? Optional.empty()
          : Optional.of(json.readValue(value, new TypeReference<>() {}));
    } catch (Exception e) {
      log.warn("Analysis comparison cache read failed");
      log.debug("Cache read detail", e);
      return Optional.empty();
    }
  }

  public void putComparison(
      long user, long dataset, List<DatasetAnalysisComparisonResponse> value) {
    if (!value.isEmpty()) write(comparisonKey(user, dataset), value, properties.comparisonTtl());
  }

  public void evict(long user, long file, long dataset) {
    try {
      redis.delete(List.of(latestKey(user, file), comparisonKey(user, dataset)));
    } catch (RuntimeException e) {
      log.warn("Analysis cache invalidation failed");
      log.debug("Cache invalidation detail", e);
    }
  }

  private <T> Optional<T> read(String key, Class<T> type) {
    try {
      String value = redis.opsForValue().get(key);
      return value == null ? Optional.empty() : Optional.of(json.readValue(value, type));
    } catch (Exception e) {
      log.warn("Analysis cache read failed");
      log.debug("Cache read detail", e);
      return Optional.empty();
    }
  }

  private void write(String key, Object value, java.time.Duration ttl) {
    try {
      redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
    } catch (Exception e) {
      log.warn("Analysis cache write failed");
      log.debug("Cache write detail", e);
    }
  }

  static String latestKey(long user, long file) {
    return "analysis:latest:" + user + ":" + file;
  }

  static String comparisonKey(long user, long dataset) {
    return "analysis:comparison:" + user + ":" + dataset;
  }
}
