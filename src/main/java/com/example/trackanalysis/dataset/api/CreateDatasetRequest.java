package com.example.trackanalysis.dataset.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDatasetRequest(
    @NotBlank(message = "name is required")
        @Size(max = 128, message = "name must not exceed 128 characters")
        String name,
    @Size(max = 500, message = "description must not exceed 500 characters") String description) {}
