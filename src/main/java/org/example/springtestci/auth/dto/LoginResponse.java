package org.example.springtestci.auth.dto;

/** 토큰은 HttpOnly 쿠키로만 내려가고 응답 본문에는 담지 않는다. */
public record LoginResponse(long expiresIn, UserResponse user) {}
