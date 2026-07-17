package com.example.trackanalysis.storage;

import java.io.InputStream;

public interface ObjectStorageService {
  void ensureBucket();

  void put(String objectName, InputStream input, long size, String contentType);

  InputStream get(String objectName);

  void deleteBestEffort(String objectName);
}
