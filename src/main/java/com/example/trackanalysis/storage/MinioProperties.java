package com.example.trackanalysis.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storage.minio")
public record MinioProperties(String endpoint, String accessKey, String secretKey, String bucket) {}
