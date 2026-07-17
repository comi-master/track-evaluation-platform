package com.example.trackanalysis.report.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class ReportRequestValidationTest {
  @Test
  void titleMustContainAtMostTwoHundredNonWhitespaceCharacters() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertThat(validator.validate(new CreateReportRequest(" "))).isNotEmpty();
      assertThat(validator.validate(new CreateReportRequest("x".repeat(201)))).isNotEmpty();
      assertThat(validator.validate(new CreateReportRequest(" valid "))).isEmpty();
    }
  }
}
