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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
  private final String dummyHash;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      RefreshTokenStore refreshTokenStore) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.refreshTokenStore = refreshTokenStore;
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
    try {
      return UserResponse.from(userRepository.saveAndFlush(user));
    } catch (DataIntegrityViolationException exception) {
      // 동시 가입으로 사전 검사를 통과한 경우 DB 유니크 제약에서 걸러진다.
      String cause = String.valueOf(exception.getMostSpecificCause().getMessage());
      throw new CommonException(
          cause.contains("uk_users_nickname")
              ? ErrorCode.DUPLICATE_NICKNAME
              : ErrorCode.DUPLICATE_EMAIL);
    }
  }

  @Transactional(readOnly = true)
  public AuthTokens login(LoginRequest request) {
    User user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
    String hash = user == null ? dummyHash : user.getPasswordHash();
    boolean matches = passwordEncoder.matches(request.password(), hash);
    if (user == null || !matches) {
      throw new CommonException(ErrorCode.INVALID_CREDENTIALS);
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

  public void logout(String refreshToken) {
    if (refreshToken != null && !refreshToken.isBlank()) {
      refreshTokenStore.revoke(refreshToken);
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

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
