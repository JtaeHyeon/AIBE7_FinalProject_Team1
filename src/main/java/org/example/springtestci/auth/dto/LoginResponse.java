package org.example.springtestci.auth.dto;

public record LoginResponse(
    String accessToken, String tokenType, long expiresIn, UserResponse user) {

  public static LoginResponse bearer(String accessToken, long expiresIn, UserResponse user) {
    return new LoginResponse(accessToken, "Bearer", expiresIn, user);
  }
}
