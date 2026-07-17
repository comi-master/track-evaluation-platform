package com.example.trackanalysis.user.application;

import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UsernamePolicy {

  private static final Pattern VALID_USERNAME = Pattern.compile("[a-z0-9._-]{3,64}");

  private UsernamePolicy() {}

  public static String normalize(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    if (!VALID_USERNAME.matcher(normalized).matches()) {
      throw new BusinessException(
          ErrorCode.INVALID_ARGUMENT,
          "Username must be 3-64 characters using letters, numbers, dot, underscore, or hyphen");
    }
    return normalized;
  }
}
