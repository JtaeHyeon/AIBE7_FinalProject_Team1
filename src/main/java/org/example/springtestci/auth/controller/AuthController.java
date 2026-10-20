package org.example.springtestci.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.example.springtestci.auth.dto.AuthTokens;
import org.example.springtestci.auth.dto.EmailResendRequest;
import org.example.springtestci.auth.dto.LoginRequest;
import org.example.springtestci.auth.dto.LoginResponse;
import org.example.springtestci.auth.dto.SignupRequest;
import org.example.springtestci.auth.dto.UserResponse;
import org.example.springtestci.auth.service.AuthService;
import org.example.springtestci.auth.service.EmailVerificationService;
import org.example.springtestci.common.response.ApiResponse;
import org.example.springtestci.common.security.JwtProperties;
import org.example.springtestci.common.security.SecurityConfig;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final String REFRESH_COOKIE = "refresh_token";
  private static final String REFRESH_COOKIE_PATH = "/api/auth";

  private final AuthService authService;
  private final EmailVerificationService emailVerificationService;
  private final JwtProperties properties;

  public AuthController(
      AuthService authService,
      EmailVerificationService emailVerificationService,
      JwtProperties properties) {
    this.authService = authService;
    this.emailVerificationService = emailVerificationService;
    this.properties = properties;
  }

  /** 아직 인증하지 못한 계정에 인증 메일을 다시 보낸다. 계정이 있는지는 응답으로 알려 주지 않는다. */
  @PostMapping("/email/resend")
  public ResponseEntity<Void> resendEmailLink(@Valid @RequestBody EmailResendRequest request) {
    emailVerificationService.resend(request.email());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<UserResponse>> signup(
      @Valid @RequestBody SignupRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(authService.signup(request)));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    return withTokenCookies(authService.login(request, httpRequest.getRemoteAddr()));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<LoginResponse>> refresh(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
    return withTokenCookies(authService.refresh(refreshToken));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
      @CookieValue(name = SecurityConfig.ACCESS_TOKEN_COOKIE, required = false) String accessCookie,
      @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    authService.logout(refreshToken, bearerOrCookie(authorization, accessCookie));
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
        .header(HttpHeaders.SET_COOKIE, accessCookie("", Duration.ZERO).toString())
        .build();
  }

  @GetMapping("/me")
  public ApiResponse<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(authService.getMe(Long.parseLong(jwt.getSubject())));
  }

  private static String bearerOrCookie(String authorization, String accessCookie) {
    if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return authorization.substring(7).trim();
    }
    return accessCookie;
  }

  private ResponseEntity<ApiResponse<LoginResponse>> withTokenCookies(AuthTokens tokens) {
    LoginResponse body = new LoginResponse(tokens.expiresIn(), tokens.user());
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            refreshCookie(tokens.refreshToken(), properties.refreshTokenTtl()).toString())
        .header(
            HttpHeaders.SET_COOKIE,
            accessCookie(tokens.accessToken(), properties.accessTokenTtl()).toString())
        .body(ApiResponse.success(body));
  }

  private ResponseCookie refreshCookie(String value, Duration maxAge) {
    return httpOnlyCookie(REFRESH_COOKIE, value, REFRESH_COOKIE_PATH, maxAge);
  }

  private ResponseCookie accessCookie(String value, Duration maxAge) {
    return httpOnlyCookie(SecurityConfig.ACCESS_TOKEN_COOKIE, value, "/", maxAge);
  }

  private ResponseCookie httpOnlyCookie(String name, String value, String path, Duration maxAge) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(properties.refreshCookieSecure())
        .sameSite("Strict")
        .path(path)
        .maxAge(maxAge)
        .build();
  }
}
