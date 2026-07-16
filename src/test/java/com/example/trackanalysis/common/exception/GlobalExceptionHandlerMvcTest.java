package com.example.trackanalysis.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.trackanalysis.common.logging.RequestIdFilter;
import com.jayway.jsonpath.JsonPath;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@Import(GlobalExceptionHandlerMvcTest.ValidationTestController.class)
class GlobalExceptionHandlerMvcTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void mapsMethodArgumentNotValidExceptionThroughMvc() throws Exception {
    MvcResult result =
        performInvalidRequest(
                """
                {"name":"x","code":"valid-code","start":1,"end":2}
                """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
            .andExpect(jsonPath("$.message").value("name: name length must be between 3 and 10"))
            .andReturn();

    assertSafeConsistentRequestId(result);
  }

  @Test
  void mapsObjectLevelValidationErrorThroughMvc() throws Exception {
    MvcResult result =
        performInvalidRequest(
                """
                {"name":"valid","code":"valid-code","start":10,"end":2}
                """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
            .andExpect(jsonPath("$.message").value("start must not exceed end"))
            .andReturn();

    assertSafeConsistentRequestId(result);
  }

  @Test
  void mapsUnreadableJsonThroughMvcWithoutLeakingParserDetails() throws Exception {
    MvcResult result =
        performInvalidRequest("{\"name\":")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
            .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_ARGUMENT.defaultMessage()))
            .andReturn();

    assertSafeConsistentRequestId(result);
    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .doesNotContain("Jackson")
        .doesNotContain("JsonParseException")
        .doesNotContain("ValidationRequest")
        .doesNotContain("com.example");
  }

  @Test
  void returnsMultipleValidationErrorsInStableSortedOrder() throws Exception {
    MvcResult result =
        performInvalidRequest(
                """
                {"name":"","code":"","start":1,"end":2}
                """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "code: code is required; name: name is required; name: name length must be"
                            + " between 3 and 10"))
            .andReturn();

    assertSafeConsistentRequestId(result);
  }

  private org.springframework.test.web.servlet.ResultActions performInvalidRequest(String json)
      throws Exception {
    return mockMvc.perform(
        post("/test/validation")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
            .header(RequestIdFilter.REQUEST_ID_HEADER, "mvc-request-12345678"));
  }

  private void assertSafeConsistentRequestId(MvcResult result) throws Exception {
    String headerRequestId = result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER);
    String bodyRequestId = JsonPath.read(result.getResponse().getContentAsString(), "$.requestId");
    String message = JsonPath.read(result.getResponse().getContentAsString(), "$.message");

    assertThat(headerRequestId).isEqualTo("mvc-request-12345678");
    assertThat(bodyRequestId).isEqualTo(headerRequestId);
    assertThat(message)
        .isNotBlank()
        .doesNotContain("Validator")
        .doesNotContain("ConstraintViolation")
        .doesNotContain("com.example");
  }

  @RestController
  @RequestMapping("/test/validation")
  static class ValidationTestController {

    @PostMapping
    void validate(@Valid @RequestBody ValidationRequest request) {}
  }

  @ValidRange
  record ValidationRequest(
      @NotBlank(message = "name is required")
          @Size(min = 3, max = 10, message = "name length must be between 3 and 10")
          String name,
      @NotBlank(message = "code is required") String code,
      Integer start,
      Integer end) {}

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Constraint(validatedBy = ValidRangeValidator.class)
  @interface ValidRange {
    String message() default "start must not exceed end";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
  }

  public static class ValidRangeValidator
      implements ConstraintValidator<ValidRange, ValidationRequest> {

    @Override
    public boolean isValid(ValidationRequest value, ConstraintValidatorContext context) {
      return value == null
          || value.start() == null
          || value.end() == null
          || value.start() <= value.end();
    }
  }
}
