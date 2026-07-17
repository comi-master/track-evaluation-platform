package com.example.trackanalysis.dataset.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateDatasetRequest(
    @NotBlank(message = "name is required")
        @Size(max = 128, message = "name must not exceed 128 characters")
        String name,
    @Size(max = 500, message = "description must not exceed 500 characters") String description,
    @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must not be negative")
        Integer version) {}
