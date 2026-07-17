package com.example.trackanalysis.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AuthRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void validatesPasswordLengthAndDoesNotExposeItThroughToString() {
    RegisterRequest invalid = new RegisterRequest("valid-user", "short");
    RegisterRequest valid = new RegisterRequest("valid-user", "sensitive-password");

    assertThat(validator.validate(invalid))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("password");
    assertThat(validator.validate(valid)).isEmpty();
    assertThat(valid.toString()).doesNotContain("sensitive-password").contains("<redacted>");
  }

  @Test
  void loginRequestAlsoRedactsThePassword() {
    LoginRequest request = new LoginRequest("valid-user", "another-password");

    assertThat(validator.validate(request)).isEmpty();
    assertThat(request.toString()).doesNotContain("another-password").contains("<redacted>");
  }

  @Test
  void loginResponseRedactsTheAccessToken() {
    LoginResponse response =
        new LoginResponse(
            "signed-sensitive-token", "Bearer", 7200, new UserResponse(1, "user", "ACTIVE"));

    assertThat(response.toString())
        .doesNotContain("signed-sensitive-token")
        .contains("accessToken=<redacted>");
  }
}
