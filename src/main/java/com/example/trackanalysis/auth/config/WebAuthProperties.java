package com.example.trackanalysis.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record WebAuthProperties(
    boolean publicRegistrationEnabled, String adminUsername, String adminPassword) {}
