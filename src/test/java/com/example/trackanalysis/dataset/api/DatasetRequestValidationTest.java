package com.example.trackanalysis.dataset.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class DatasetRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void validatesCreateNameAndDescriptionBoundaries() {
    assertThat(validator.validate(new CreateDatasetRequest("", null))).isNotEmpty();
    assertThat(validator.validate(new CreateDatasetRequest("name", "x".repeat(501)))).isNotEmpty();
    assertThat(validator.validate(new CreateDatasetRequest("name", "description"))).isEmpty();
  }

  @Test
  void requiresANonNegativeUpdateVersion() {
    assertThat(validator.validate(new UpdateDatasetRequest("name", null, null))).isNotEmpty();
    assertThat(validator.validate(new UpdateDatasetRequest("name", null, -1))).isNotEmpty();
    assertThat(validator.validate(new UpdateDatasetRequest("name", null, 0))).isEmpty();
  }
}
