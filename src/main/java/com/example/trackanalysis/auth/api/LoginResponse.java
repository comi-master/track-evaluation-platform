package com.example.trackanalysis.auth.api;

public record LoginResponse(
    String accessToken, String tokenType, long expiresIn, UserResponse user) {

  @Override
  public String toString() {
    return "LoginResponse[accessToken=<redacted>, tokenType="
        + tokenType
        + ", expiresIn="
        + expiresIn
        + ", user="
        + user
        + "]";
  }
}
