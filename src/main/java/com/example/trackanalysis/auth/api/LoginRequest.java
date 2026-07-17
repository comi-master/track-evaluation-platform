package com.example.trackanalysis.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "username is required") @Size(max = 128, message = "username is too long")
        String username,
    @NotBlank(message = "password is required")
        @Size(min = 8, max = 64, message = "password length must be between 8 and 64")
        String password) {

  @Override
  public String toString() {
    return "LoginRequest[username=" + username + ", password=<redacted>]";
  }
}
