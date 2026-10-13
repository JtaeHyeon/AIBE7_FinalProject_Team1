package org.example.springtestci.auth.controller;

import jakarta.validation.Valid;
import java.time.Duration;
import org.example.springtestci.auth.dto.AuthTokens;
import org.example.springtestci.auth.dto.LoginRequest;
import org.example.springtestci.auth.dto.LoginResponse;
import org.example.springtestci.auth.dto.SignupRequest;
import org.example.springtestci.auth.dto.UserResponse;
import org.example.springtestci.auth.service.AuthService;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.common.security.JwtProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final String REFRESH_COOKIE = "refresh_token";
  private static final String REFRESH_COOKIE_PATH = "/api/auth";

  private final AuthService authService;
  private final JwtProperties properties;

  public AuthController(AuthService authService, JwtProperties properties) {
    this.authService = authService;
    this.properties = properties;
  }

  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<UserResponse>> signup(
      @Valid @RequestBody SignupRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(authService.signup(request)));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    return withRefreshCookie(authService.login(request));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<LoginResponse>> refresh(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
    return withRefreshCookie(authService.refresh(refreshToken));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
    authService.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
        .build();
  }

  @GetMapping("/me")
  public ApiResponse<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(authService.getMe(Long.parseLong(jwt.getSubject())));
  }

  private ResponseEntity<ApiResponse<LoginResponse>> withRefreshCookie(AuthTokens tokens) {
    LoginResponse body =
        LoginResponse.bearer(tokens.accessToken(), tokens.expiresIn(), tokens.user());
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            refreshCookie(tokens.refreshToken(), properties.refreshTokenTtl()).toString())
        .body(ApiResponse.success(body));
  }

  private ResponseCookie refreshCookie(String value, Duration maxAge) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(properties.refreshCookieSecure())
        .sameSite("Strict")
        .path(REFRESH_COOKIE_PATH)
        .maxAge(maxAge)
        .build();
  }
}
