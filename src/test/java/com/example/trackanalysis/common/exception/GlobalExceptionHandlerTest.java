package com.example.trackanalysis.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trackanalysis.common.api.Result;
import com.example.trackanalysis.common.logging.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void mapsBusinessExceptionToItsHttpStatusAndSafeEnvelope() {
    MockHttpServletRequest request = requestWithId("request-12345678");
    BusinessException exception =
        new BusinessException(ErrorCode.CONFLICT, "Dataset name already exists");

    ResponseEntity<Result<Void>> response = handler.handleBusinessException(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(ErrorCode.CONFLICT.httpStatus());
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("CONFLICT");
    assertThat(response.getBody().message()).isEqualTo("Dataset name already exists");
    assertThat(response.getBody().requestId()).isEqualTo("request-12345678");
  }

  @Test
  void hidesUnexpectedExceptionDetails() {
    MockHttpServletRequest request = requestWithId("request-87654321");

    ResponseEntity<Result<Void>> response =
        handler.handleUnexpectedException(new IllegalStateException("secret detail"), request);

    assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.httpStatus());
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
  }

  @Test
  void includesFieldNameAndSafeValidationMessage() {
    MockHttpServletRequest request = requestWithId("request-11111111");
    BindException exception = new BindException(new Object(), "request");
    exception.addError(new FieldError("request", "name", "must not be blank"));

    ResponseEntity<Result<Void>> response = handler.handleBindingException(exception, request);

    assertInvalidResponse(response, "name: must not be blank", "request-11111111");
  }

  @Test
  void includesSafeGlobalValidationMessage() {
    MockHttpServletRequest request = requestWithId("request-22222222");
    BindException exception = new BindException(new Object(), "request");
    exception.addError(new ObjectError("request", "Coordinates are inconsistent"));

    ResponseEntity<Result<Void>> response = handler.handleBindingException(exception, request);

    assertInvalidResponse(response, "Coordinates are inconsistent", "request-22222222");
  }

  @Test
  void usesDefaultMessageWhenBindingErrorsHaveNoPublicMessage() {
    MockHttpServletRequest request = requestWithId("request-33333333");
    BindException exception = new BindException(new Object(), "request");
    exception.addError(new ObjectError("request", null));
    exception.addError(new ObjectError("request", "  "));

    ResponseEntity<Result<Void>> response = handler.handleBindingException(exception, request);

    assertInvalidResponse(
        response, ErrorCode.INVALID_ARGUMENT.defaultMessage(), "request-33333333");
  }

  @Test
  void hidesUnreadableRequestParserDetails() {
    MockHttpServletRequest request = requestWithId("request-44444444");
    HttpMessageNotReadableException exception =
        new HttpMessageNotReadableException(
            "internal parser detail", new MockHttpInputMessage(new byte[0]));

    ResponseEntity<Result<Void>> response = handler.handleInvalidRequest(exception, request);

    assertInvalidResponse(
        response, ErrorCode.INVALID_ARGUMENT.defaultMessage(), "request-44444444");
    assertThat(response.getBody().message()).doesNotContain("internal parser detail");
  }

  private void assertInvalidResponse(
      ResponseEntity<Result<Void>> response, String message, String requestId) {
    assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT.httpStatus());
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_ARGUMENT.code());
    assertThat(response.getBody().message()).isEqualTo(message);
    assertThat(response.getBody().requestId()).isEqualTo(requestId);
  }

  private MockHttpServletRequest requestWithId(String requestId) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, requestId);
    return request;
  }
}
