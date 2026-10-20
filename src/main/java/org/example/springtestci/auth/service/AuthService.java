package org.example.springtestci.auth.service;

import java.util.Locale;
import org.example.springtestci.auth.dto.AuthTokens;
import org.example.springtestci.auth.dto.LoginRequest;
import org.example.springtestci.auth.dto.SignupRequest;
import org.example.springtestci.auth.dto.UserResponse;
import org.example.springtestci.auth.entity.User;
import org.example.springtestci.auth.entity.UserRole;
import org.example.springtestci.auth.repository.UserRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.security.AccessTokenBlocklist;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  /** 없는 이메일로 로그인해도 해시 비교 비용을 치르게 해, 계정 존재 여부를 응답 시간으로 알 수 없게 한다. */
  private static final String DUMMY_PASSWORD = "dummy-password-for-timing";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenStore refreshTokenStore;
  private final EmailVerificationService emailVerificationService;
  private final LoginRateLimiter loginRateLimiter;
  private final JwtDecoder jwtDecoder;
  private final AccessTokenBlocklist accessTokenBlocklist;
  private final String dummyHash;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      RefreshTokenStore refreshTokenStore,
      EmailVerificationService emailVerificationService,
      LoginRateLimiter loginRateLimiter,
      JwtDecoder jwtDecoder,
      AccessTokenBlocklist accessTokenBlocklist) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.refreshTokenStore = refreshTokenStore;
    this.emailVerificationService = emailVerificationService;
    this.loginRateLimiter = loginRateLimiter;
    this.jwtDecoder = jwtDecoder;
    this.accessTokenBlocklist = accessTokenBlocklist;
    this.dummyHash = passwordEncoder.encode(DUMMY_PASSWORD);
  }

  @Transactional
  public UserResponse signup(SignupRequest request) {
    String email = normalizeEmail(request.email());
    String nickname = request.nickname().trim();
    if (userRepository.existsByEmail(email)) {
      throw new CommonException(ErrorCode.DUPLICATE_EMAIL);
    }
    if (userRepository.existsByNickname(nickname)) {
      throw new CommonException(ErrorCode.DUPLICATE_NICKNAME);
    }

    User user =
        new User(email, passwordEncoder.encode(request.password()), nickname, UserRole.USER);
    User saved;
    try {
      saved = userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException exception) {
      // 동시 가입으로 사전 검사를 통과한 경우 DB 유니크 제약에서 걸러진다.
      String cause = String.valueOf(exception.getMostSpecificCause().getMessage());
      throw new CommonException(
          cause.contains("uk_users_nickname")
              ? ErrorCode.DUPLICATE_NICKNAME
              : ErrorCode.DUPLICATE_EMAIL);
    }
    // 메일을 보내지 못하면 예외로 가입 전체를 되돌려, 인증할 수 없는 계정이 남지 않게 한다.
    emailVerificationService.sendLink(email);
    return UserResponse.from(saved);
  }

  @Transactional
  public AuthTokens login(LoginRequest request, String clientIp) {
    String email = normalizeEmail(request.email());
    // 제한 중에는 비밀번호를 비교하지 않고 바로 거부한다.
    loginRateLimiter.checkAllowed(email, clientIp);
    User user = userRepository.findByEmail(email).orElse(null);
    String hash = user == null ? dummyHash : user.getPasswordHash();
    boolean matches = passwordEncoder.matches(request.password(), hash);
    if (user == null || !matches) {
      loginRateLimiter.recordFailure(email, clientIp);
      throw new CommonException(ErrorCode.INVALID_CREDENTIALS);
    }
    loginRateLimiter.reset(email);
    // 비밀번호가 맞은 뒤에만 인증 여부를 알려, 미인증 계정의 존재를 함부로 드러내지 않는다.
    if (!user.isEmailVerified()) {
      if (!emailVerificationService.confirmIfVerified(user.getEmail())) {
        throw new CommonException(ErrorCode.EMAIL_NOT_VERIFIED);
      }
      user.markEmailVerified();
    }
    return issueTokens(user);
  }

  /** Refresh Token을 한 번 쓰고 폐기한 뒤 새 토큰 쌍을 발급한다(rotation). */
  @Transactional(readOnly = true)
  public AuthTokens refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new CommonException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
    Long userId =
        refreshTokenStore
            .consume(refreshToken)
            .orElseThrow(() -> new CommonException(ErrorCode.INVALID_REFRESH_TOKEN));
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CommonException(ErrorCode.INVALID_REFRESH_TOKEN));
    return issueTokens(user);
  }

  /** Refresh Token을 폐기하고, Access Token은 남은 유효 기간 동안 사용하지 못하게 차단한다. */
  public void logout(String refreshToken, String accessToken) {
    if (refreshToken != null && !refreshToken.isBlank()) {
      refreshTokenStore.revoke(refreshToken);
    }
    if (accessToken != null && !accessToken.isBlank()) {
      try {
        accessTokenBlocklist.block(jwtDecoder.decode(accessToken));
      } catch (JwtException exception) {
        // 이미 만료됐거나 유효하지 않은 토큰은 차단할 필요가 없다.
      }
    }
  }

  @Transactional(readOnly = true)
  public UserResponse getMe(long userId) {
    return userRepository
        .findById(userId)
        .map(UserResponse::from)
        .orElseThrow(() -> new CommonException(ErrorCode.UNAUTHORIZED));
  }

  private AuthTokens issueTokens(User user) {
    return new AuthTokens(
        jwtTokenProvider.createAccessToken(user),
        jwtTokenProvider.accessTokenExpiresInSeconds(),
        refreshTokenStore.issue(user.getId()),
        UserResponse.from(user));
  }

  static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
