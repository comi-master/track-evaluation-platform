package com.example.trackanalysis.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  INVALID_ARGUMENT("INVALID_ARGUMENT", "Invalid request parameters", HttpStatus.BAD_REQUEST),
  UNAUTHORIZED("UNAUTHORIZED", "Authentication is required", HttpStatus.UNAUTHORIZED),
  FORBIDDEN("FORBIDDEN", "Access is denied", HttpStatus.FORBIDDEN),
  RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Resource was not found", HttpStatus.NOT_FOUND),
  CONFLICT("CONFLICT", "The request conflicts with current state", HttpStatus.CONFLICT),
  FILE_FORMAT_ERROR("FILE_FORMAT_ERROR", "The file format is invalid", HttpStatus.BAD_REQUEST),
  TASK_STATE_ERROR(
      "TASK_STATE_ERROR", "The task state does not allow this operation", HttpStatus.CONFLICT),
  INFRASTRUCTURE_ERROR(
      "INFRASTRUCTURE_ERROR",
      "A required service is temporarily unavailable",
      HttpStatus.SERVICE_UNAVAILABLE),
  INTERNAL_ERROR(
      "INTERNAL_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);

  private final String code;
  private final String defaultMessage;
  private final HttpStatus httpStatus;

  ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
    this.code = code;
    this.defaultMessage = defaultMessage;
    this.httpStatus = httpStatus;
  }

  public String code() {
    return code;
  }

  public String defaultMessage() {
    return defaultMessage;
  }

  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
