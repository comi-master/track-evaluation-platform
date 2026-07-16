package com.example.trackanalysis.common.api;

import com.example.trackanalysis.common.exception.ErrorCode;
import java.time.Instant;

public record Result<T>(String code, String message, T data, String requestId, Instant timestamp) {

  public static <T> Result<T> success(T data, String requestId) {
    return new Result<>("SUCCESS", "success", data, requestId, Instant.now());
  }

  public static Result<Void> failure(ErrorCode errorCode, String message, String requestId) {
    return new Result<>(errorCode.code(), message, null, requestId, Instant.now());
  }
}
