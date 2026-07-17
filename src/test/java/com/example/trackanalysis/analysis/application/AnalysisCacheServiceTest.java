package com.example.trackanalysis.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.trackanalysis.analysis.api.AnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AnalysisCacheServiceTest {
  StringRedisTemplate redis = mock(StringRedisTemplate.class);
  ValueOperations<String, String> values = mock(ValueOperations.class);
  ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
  AnalysisCacheService cache;

  @BeforeEach
  void setUp() {
    when(redis.opsForValue()).thenReturn(values);
    cache =
        new AnalysisCacheService(
            redis,
            json,
            new AnalysisCacheProperties(Duration.ofMinutes(10), Duration.ofMinutes(5)));
  }

  @Test
  void userScopedKeysPreventCrossUserCacheLeaks() {
    assertThat(AnalysisCacheService.latestKey(1, 9))
        .isNotEqualTo(AnalysisCacheService.latestKey(2, 9));
    assertThat(AnalysisCacheService.comparisonKey(1, 7))
        .isNotEqualTo(AnalysisCacheService.comparisonKey(2, 7));
  }

  @Test
  void latestMissFallsThroughAndHitDeserializes() throws Exception {
    AnalysisResponse response = response();
    when(values.get("analysis:latest:3:4")).thenReturn(null, json.writeValueAsString(response));
    assertThat(cache.latest(3, 4)).isEmpty();
    assertThat(cache.latest(3, 4)).contains(response);
  }

  @Test
  void writesWithConfiguredTtlAndEvictsBothAffectedViews() {
    cache.putLatest(3, 4, response());
    verify(values).set(eq("analysis:latest:3:4"), any(), eq(Duration.ofMinutes(10)));
    cache.evict(3, 4, 8);
    verify(redis).delete(List.of("analysis:latest:3:4", "analysis:comparison:3:8"));
  }

  @Test
  void redisFailuresDegradeToDatabasePathAndNeverEscape() {
    when(values.get(any())).thenThrow(new IllegalStateException("secret endpoint"));
    assertThat(cache.latest(3, 4)).isEmpty();
    doThrow(new IllegalStateException("down")).when(redis).delete(any(List.class));
    cache.evict(3, 4, 8);
  }

  @Test
  void emptyComparisonIsNotCached() {
    cache.putComparison(3, 8, List.of());
    verify(values, never()).set(any(), any(), any(Duration.class));
  }

  private AnalysisResponse response() {
    return new AnalysisResponse(
        1, 4, 2, 1, 3, 3, 3, 3, 0, 1, 1, 7, LocalDateTime.of(2026, 1, 1, 0, 0), List.of());
  }
}
