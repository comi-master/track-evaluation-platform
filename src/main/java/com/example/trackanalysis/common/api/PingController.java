package com.example.trackanalysis.common.api;

import com.example.trackanalysis.common.logging.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PingController {

  @GetMapping("/ping")
  public Result<PingResponse> ping(HttpServletRequest request) {
    PingResponse response = new PingResponse("ok", "track-analysis-platform");
    return Result.success(response, RequestIdFilter.requestId(request));
  }

  public record PingResponse(String status, String application) {}
}
