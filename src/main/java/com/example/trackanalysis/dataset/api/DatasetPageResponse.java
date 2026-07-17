package com.example.trackanalysis.dataset.api;

import java.util.List;

public record DatasetPageResponse(
    long page, long size, long total, long pages, List<DatasetResponse> items) {

  public DatasetPageResponse {
    items = List.copyOf(items);
  }
}
