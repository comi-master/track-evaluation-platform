package com.example.trackanalysis.common.exception;

import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Result<Void>> handleBusinessException(
      BusinessException exception, HttpServletRequest request) {
    ErrorCode errorCode = exception.errorCode();
    if (exception.getCause() == null) {
      log.warn(
          "Business request failed with code {}: {}", errorCode.code(), exception.getMessage());
    } else {
      log.warn("Business request failed with code {}", errorCode.code(), exception);
    }
    return response(errorCode, exception.getMessage(), request);
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
  public ResponseEntity<Result<Void>> handleBindingException(
      Exception exception, HttpServletRequest request) {
    BindingResult bindingResult =
        exception instanceof MethodArgumentNotValidException methodException
            ? methodException.getBindingResult()
            : ((BindException) exception).getBindingResult();
    String message =
        bindingResult.getAllErrors().stream()
            .map(this::validationMessage)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.joining("; "));
    if (message.isBlank()) {
      message = ErrorCode.INVALID_ARGUMENT.defaultMessage();
    }
    return response(ErrorCode.INVALID_ARGUMENT, message, request);
  }

  @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<Result<Void>> handleInvalidRequest(
      Exception exception, HttpServletRequest request) {
    log.debug("Request validation failed", exception);
    return response(
        ErrorCode.INVALID_ARGUMENT, ErrorCode.INVALID_ARGUMENT.defaultMessage(), request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Result<Void>> handleUnexpectedException(
      Exception exception, HttpServletRequest request) {
    log.error("Unexpected request failure", exception);
    return response(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), request);
  }

  private ResponseEntity<Result<Void>> response(
      ErrorCode errorCode, String message, HttpServletRequest request) {
    String requestId = RequestIdFilter.requestId(request);
    Result<Void> body = Result.failure(errorCode, message, requestId);
    return ResponseEntity.status(errorCode.httpStatus()).body(body);
  }

  private String validationMessage(ObjectError error) {
    String defaultMessage = error.getDefaultMessage();
    if (defaultMessage == null || defaultMessage.isBlank()) {
      return null;
    }
    return error instanceof FieldError fieldError
        ? fieldError.getField() + ": " + defaultMessage
        : defaultMessage;
  }
}
