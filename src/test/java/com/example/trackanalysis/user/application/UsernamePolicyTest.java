package com.example.trackanalysis.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.trackanalysis.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class UsernamePolicyTest {

  @Test
  void trimsAndLowercasesAValidUsername() {
    assertThat(UsernamePolicy.normalize("  Researcher.01  ")).isEqualTo("researcher.01");
  }

  @Test
  void acceptsTheDocumentedSafeCharacters() {
    assertThat(UsernamePolicy.normalize("user_name-01.test")).isEqualTo("user_name-01.test");
  }

  @Test
  void rejectsWhitespaceAndUnsupportedCharacters() {
    assertThatThrownBy(() -> UsernamePolicy.normalize("user name"))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> UsernamePolicy.normalize("用户01"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void rejectsNamesOutsideTheLengthBoundary() {
    assertThatThrownBy(() -> UsernamePolicy.normalize("ab")).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> UsernamePolicy.normalize("a".repeat(65)))
        .isInstanceOf(BusinessException.class);
  }
}
