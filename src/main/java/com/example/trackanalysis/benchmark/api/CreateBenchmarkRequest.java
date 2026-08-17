package com.example.trackanalysis.benchmark.api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CreateBenchmarkRequest(@NotBlank @Size(max=128) String name, @Size(max=500) String description) {}
