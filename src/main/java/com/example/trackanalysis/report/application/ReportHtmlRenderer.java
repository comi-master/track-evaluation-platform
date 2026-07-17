package com.example.trackanalysis.report.application;

import com.example.trackanalysis.report.infrastructure.persistence.ReportSourceRow;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class ReportHtmlRenderer {
  public String render(
      String title, String datasetName, LocalDateTime createdAt, List<ReportSourceRow> rows) {
    StringBuilder b =
        new StringBuilder(
                "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>")
            .append(e(title))
            .append(
                "</title><style>body{font-family:sans-serif;max-width:1200px;margin:2rem"
                    + " auto;color:#222}table{border-collapse:collapse;width:100%}th,td{border:1px"
                    + " solid"
                    + " #bbb;padding:.4rem;text-align:right}th:first-child,td:first-child{text-align:left}@media"
                    + " print{body{margin:0}}</style></head><body><h1>")
            .append(e(title))
            .append("</h1><p>数据集：")
            .append(e(datasetName))
            .append("</p><p>生成时间（UTC）：")
            .append(createdAt)
            .append("；航迹文件数：")
            .append(rows.size())
            .append("</p>");
    b.append(
        "<h2>多来源对比</h2><table><thead><tr><th>文件 /"
            + " 来源</th><th>点数</th><th>阈值</th><th>平均误差</th><th>RMSE</th><th>最小</th><th>最大</th><th>总体标准差</th><th>异常数"
            + " / 比例</th><th>最大误差时间</th><th>分析时间</th></tr></thead><tbody>");
    for (ReportSourceRow r : rows)
      b.append("<tr><td>")
          .append(e(r.originalName()))
          .append(" / ")
          .append(e(r.trackSource()))
          .append("</td><td>")
          .append(r.pointCount())
          .append("</td><td>")
          .append(r.abnormalThreshold())
          .append("</td><td>")
          .append(r.meanError())
          .append("</td><td>")
          .append(r.rmse())
          .append("</td><td>")
          .append(r.minError())
          .append("</td><td>")
          .append(r.maxError())
          .append("</td><td>")
          .append(r.standardDeviation())
          .append("</td><td>")
          .append(r.abnormalCount())
          .append(" / ")
          .append(r.abnormalRatio())
          .append("</td><td>")
          .append(r.maxErrorTime())
          .append("</td><td>")
          .append(r.analyzedAt())
          .append("</td></tr>");
    b.append("</tbody></table><h2>确定性摘要</h2><ul>");
    summary(
        b,
        "最低 RMSE",
        rows,
        Comparator.comparingDouble(ReportSourceRow::rmse),
        ReportSourceRow::rmse);
    summary(
        b,
        "最低平均误差",
        rows,
        Comparator.comparingDouble(ReportSourceRow::meanError),
        ReportSourceRow::meanError);
    summary(
        b,
        "最低异常比例",
        rows,
        Comparator.comparingDouble(ReportSourceRow::abnormalRatio),
        ReportSourceRow::abnormalRatio);
    return b.append(
            "</ul><h2>方法说明</h2><p>误差为三维欧氏距离；RMSE 为误差平方均值的平方根；标准差为总体标准差；仅 error &gt; threshold"
                + " 的点属于异常。</p><h2>限制</h2><p>本报告是生成时最新分析结果的不可变快照，仅描述输入数据与指定阈值，不代表算法显著性或生产部署结论。</p></body></html>")
        .toString();
  }

  private void summary(
      StringBuilder b,
      String label,
      List<ReportSourceRow> rows,
      Comparator<ReportSourceRow> c,
      java.util.function.ToDoubleFunction<ReportSourceRow> value) {
    double best =
        value.applyAsDouble(
            rows.stream().min(c.thenComparingLong(ReportSourceRow::fileId)).orElseThrow());
    b.append("<li>").append(label).append("：");
    rows.stream()
        .filter(r -> Double.compare(value.applyAsDouble(r), best) == 0)
        .sorted(Comparator.comparingLong(ReportSourceRow::fileId))
        .forEach(r -> b.append(e(r.originalName())).append("（").append(best).append("）；"));
    b.append("并列按文件 ID 稳定列出。</li>");
  }

  private String e(String value) {
    return HtmlUtils.htmlEscape(value == null ? "" : value);
  }
}
