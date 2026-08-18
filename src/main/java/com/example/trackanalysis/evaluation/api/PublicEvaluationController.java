package com.example.trackanalysis.evaluation.api;

import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.example.trackanalysis.evaluation.infrastructure.persistence.EvaluationRunMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!no-persistence")
@RequestMapping("/api/v1/public")
public class PublicEvaluationController {
  private final EvaluationRunMapper runs;

  public PublicEvaluationController(EvaluationRunMapper runs) {
    this.runs = runs;
  }

  @GetMapping("/leaderboard")
  public Result<List<LeaderboardEntry>> leaderboard(
      @RequestParam @Min(1) long benchmarkVersionId,
      @RequestParam @Min(1) long protocolId,
      @RequestParam(defaultValue = "50") int limit,
      HttpServletRequest request) {
    return Result.success(
        runs.selectLeaderboard(benchmarkVersionId, protocolId, Math.min(Math.max(limit, 1), 100)),
        RequestIdFilter.requestId(request));
  }
}
