package com.example.trackanalysis.storage;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "storage.minio.enabled", havingValue = "true", matchIfMissing = true)
public class MinioBucketInitializer implements ApplicationRunner {

  private final ObjectStorageService storage;

  public MinioBucketInitializer(ObjectStorageService storage) {
    this.storage = storage;
  }

  @Override
  public void run(ApplicationArguments args) {
    storage.ensureBucket();
  }
}
