package com.example.trackanalysis.storage;

import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MinioObjectStorageService implements ObjectStorageService {

  private static final Logger log = LoggerFactory.getLogger(MinioObjectStorageService.class);
  private final MinioClient client;
  private final String bucket;

  public MinioObjectStorageService(MinioClient client, MinioProperties properties) {
    this.client = client;
    this.bucket = properties.bucket();
  }

  @Override
  public void ensureBucket() {
    try {
      if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      }
    } catch (Exception exception) {
      throw unavailable("Object storage bucket initialization failed", exception);
    }
  }

  @Override
  public void put(String objectName, InputStream input, long size, String contentType) {
    try {
      client.putObject(
          PutObjectArgs.builder().bucket(bucket).object(objectName).stream(input, size, -1)
              .contentType(contentType)
              .build());
    } catch (Exception exception) {
      throw unavailable("Object storage upload failed", exception);
    }
  }

  @Override
  public InputStream get(String objectName) {
    try {
      return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectName).build());
    } catch (Exception exception) {
      throw unavailable("Object storage read failed", exception);
    }
  }

  @Override
  public void deleteBestEffort(String objectName) {
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
    } catch (Exception exception) {
      log.error("Best-effort object cleanup failed");
      log.debug("Object cleanup failure detail", exception);
    }
  }

  private BusinessException unavailable(String message, Exception cause) {
    return new BusinessException(ErrorCode.INFRASTRUCTURE_ERROR, message, cause);
  }
}
