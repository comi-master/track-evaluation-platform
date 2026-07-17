package com.example.trackanalysis.report.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trackanalysis.report.infrastructure.persistence.ReportSourceRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportHtmlRendererTest {
  private final ReportHtmlRenderer renderer = new ReportHtmlRenderer();

  @Test
  void escapesAllTextAndProducesStableTiedSummary() {
    LocalDateTime time = LocalDateTime.of(2026, 1, 2, 3, 4);
    var first = row(2, "<img src=x onerror=alert(1)>", "RADAR<script>", 1, time);
    var second = row(1, "safe & sound.csv", "FUSION", 1, time);
    String html = renderer.render("<script>alert(1)</script>", "A&B", time, List.of(first, second));
    assertThat(html)
        .contains(
            "&lt;script&gt;alert(1)&lt;/script&gt;",
            "A&amp;B",
            "&lt;img src=x onerror=alert(1)&gt;",
            "RADAR&lt;script&gt;")
        .doesNotContain("<script>", "onerror=alert(1)>")
        .containsSubsequence("safe &amp; sound.csv", "&lt;img src=x onerror=alert(1)&gt;")
        .contains("并列按文件 ID 稳定列出");
  }

  private ReportSourceRow row(
      long id, String name, String source, double metric, LocalDateTime time) {
    return new ReportSourceRow(id, name, source, 3, 2, metric, metric, 0, 2, 1, 1, metric, 3, time);
  }
}
