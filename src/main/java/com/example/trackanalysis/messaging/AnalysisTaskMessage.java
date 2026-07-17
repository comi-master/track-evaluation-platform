package com.example.trackanalysis.messaging;

public record AnalysisTaskMessage(int schemaVersion, long taskId) {
  public AnalysisTaskMessage(long taskId) {
    this(1, taskId);
  }
}
