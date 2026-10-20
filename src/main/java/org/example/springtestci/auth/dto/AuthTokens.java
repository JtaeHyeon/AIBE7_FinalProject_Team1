package org.example.springtestci.auth.dto;

/** 서비스 계층 내부 결과. refreshToken은 응답 본문이 아니라 HttpOnly 쿠키로만 내려간다. */
public record AuthTokens(
    String accessToken, long expiresIn, String refreshToken, UserResponse user) {}
