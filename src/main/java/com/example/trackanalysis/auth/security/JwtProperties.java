package com.example.trackanalysis.auth.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("security.jwt")
public record JwtProperties(
    @NotBlank String secret, @NotBlank String issuer, @Min(1) @Max(1440) long accessTtlMinutes) {}
