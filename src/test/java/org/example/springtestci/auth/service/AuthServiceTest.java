package org.example.springtestci.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;
import org.example.springtestci.auth.dto.AuthTokens;
import org.example.springtestci.auth.dto.LoginRequest;
import org.example.springtestci.auth.dto.SignupRequest;
import org.example.springtestci.auth.dto.UserResponse;
import org.example.springtestci.auth.entity.User;
import org.example.springtestci.auth.entity.UserRole;
import org.example.springtestci.auth.repository.UserRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private RefreshTokenStore refreshTokenStore;
  @Mock private EmailVerificationService emailVerificationService;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    when(passwordEncoder.encode("dummy-password-for-timing")).thenReturn("dummy-hash");
    authService =
        new AuthService(
            userRepository,
            passwordEncoder,
            jwtTokenProvider,
            refreshTokenStore,
            emailVerificationService);
  }

  @Test
  void signupNormalizesEmailAndStoresOnlyTheHashedPassword() {
    when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
    when(userRepository.existsByNickname("기록왕")).thenReturn(false);
    when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashed");
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

    UserResponse response =
        authService.signup(new SignupRequest("  User@Example.COM ", "Passw0rd!", " 기록왕 "));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(saved.capture());
    assertEquals("user@example.com", saved.getValue().getEmail());
    assertEquals("hashed", saved.getValue().getPasswordHash());
    assertEquals("기록왕", saved.getValue().getNickname());
    assertEquals(UserRole.USER, saved.getValue().getRole());
    assertEquals("user@example.com", response.email());
    verify(emailVerificationService).sendLink("user@example.com");
  }

  @Test
  void signupRejectsADuplicateEmail() {
    when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

    CommonException exception =
        assertThrows(
            CommonException.class,
            () -> authService.signup(new SignupRequest("user@example.com", "Passw0rd!", "기록왕")));

    assertEquals(ErrorCode.DUPLICATE_EMAIL, exception.errorCode());
    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  void signupRejectsADuplicateNickname() {
    when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
    when(userRepository.existsByNickname("기록왕")).thenReturn(true);

    CommonException exception =
        assertThrows(
            CommonException.class,
            () -> authService.signup(new SignupRequest("user@example.com", "Passw0rd!", "기록왕")));

    assertEquals(ErrorCode.DUPLICATE_NICKNAME, exception.errorCode());
  }

  @Test
  void signupFailsWhenTheVerificationMailCannotBeSent() {
    when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
    when(userRepository.existsByNickname("기록왕")).thenReturn(false);
    when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashed");
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));
    doThrow(new CommonException(ErrorCode.EMAIL_SEND_FAILED))
        .when(emailVerificationService)
        .sendLink("user@example.com");

    CommonException exception =
        assertThrows(
            CommonException.class,
            () -> authService.signup(new SignupRequest("user@example.com", "Passw0rd!", "기록왕")));

    assertEquals(ErrorCode.EMAIL_SEND_FAILED, exception.errorCode());
  }

  @Test
  void signupMapsAConcurrentUniqueViolationToADuplicateError() {
    when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
    when(userRepository.existsByNickname("기록왕")).thenReturn(false);
    when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashed");
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "conflict",
                new RuntimeException("duplicate key value violates uk_users_nickname")));

    CommonException exception =
        assertThrows(
            CommonException.class,
            () -> authService.signup(new SignupRequest("user@example.com", "Passw0rd!", "기록왕")));

    assertEquals(ErrorCode.DUPLICATE_NICKNAME, exception.errorCode());
  }

  @Test
  void loginIssuesAccessAndRefreshTokens() throws Exception {
    User user = user(7L, "user@example.com", "hashed");
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Passw0rd!", "hashed")).thenReturn(true);
    when(jwtTokenProvider.createAccessToken(user)).thenReturn("access");
    when(jwtTokenProvider.accessTokenExpiresInSeconds()).thenReturn(900L);
    when(refreshTokenStore.issue(7L)).thenReturn("refresh");

    AuthTokens tokens = authService.login(new LoginRequest(" User@Example.com", "Passw0rd!"));

    assertEquals("access", tokens.accessToken());
    assertEquals("refresh", tokens.refreshToken());
    assertEquals(900L, tokens.expiresIn());
    assertEquals(7L, tokens.user().id());
  }

  @Test
  void loginRejectsAWrongPassword() throws Exception {
    User user = user(7L, "user@example.com", "hashed");
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

    CommonException exception =
        assertThrows(
            CommonException.class,
            () -> authService.login(new LoginRequest("user@example.com", "wrong")));

    assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.errorCode());
    verifyNoInteractions(refreshTokenStore);
  }

  @Test
  void loginRejectsAnUnverifiedEmailUntilTheLinkIsClicked() throws Exception {
    User user = unverifiedUser(7L, "user@example.com", "hashed");
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Passw0rd!", "hashed")).thenReturn(true);
    when(emailVerificationService.confirmIfVerified("user@example.com")).thenReturn(false);

    CommonException exception =
        assertThrows(
            CommonException.class,
            () -> authService.login(new LoginRequest("user@example.com", "Passw0rd!")));

    assertEquals(ErrorCode.EMAIL_NOT_VERIFIED, exception.errorCode());
    assertFalse(user.isEmailVerified());
    verifyNoInteractions(refreshTokenStore);
  }

  @Test
  void loginMarksTheUserVerifiedOnceTheLinkWasClicked() throws Exception {
    User user = unverifiedUser(7L, "user@example.com", "hashed");
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Passw0rd!", "hashed")).thenReturn(true);
    when(emailVerificationService.confirmIfVerified("user@example.com")).thenReturn(true);
    when(jwtTokenProvider.createAccessToken(user)).thenReturn("access");
    when(jwtTokenProvider.accessTokenExpiresInSeconds()).thenReturn(900L);
    when(refreshTokenStore.issue(7L)).thenReturn("refresh");

    AuthTokens tokens = authService.login(new LoginRequest("user@example.com", "Passw0rd!"));

    assertTrue(user.isEmailVerified());
    assertEquals("access", tokens.accessToken());
  }

  @Test
  void loginWithAWrongPasswordNeverRevealsThatTheEmailIsUnverified() throws Exception {
    User user = unverifiedUser(7L, "user@example.com", "hashed");
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

    CommonException exception =
        assertThrows(
            CommonException.class,
            () -> authService.login(new LoginRequest("user@example.com", "wrong")));

    assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.errorCode());
    verifyNoInteractions(emailVerificationService);
  }

  @Test
  void loginForAnUnknownEmailFailsTheSameWayAndStillComparesAHash() {
    when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.matches("Passw0rd!", "dummy-hash")).thenReturn(false);

    CommonException exception =
        assertThrows(
            CommonException.class,
            () -> authService.login(new LoginRequest("nobody@example.com", "Passw0rd!")));

    assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.errorCode());
    verify(passwordEncoder).matches("Passw0rd!", "dummy-hash");
  }

  @Test
  void refreshRotatesTheTokenPair() throws Exception {
    User user = user(7L, "user@example.com", "hashed");
    when(refreshTokenStore.consume("old")).thenReturn(Optional.of(7L));
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    when(jwtTokenProvider.createAccessToken(user)).thenReturn("access2");
    when(jwtTokenProvider.accessTokenExpiresInSeconds()).thenReturn(900L);
    when(refreshTokenStore.issue(7L)).thenReturn("new");

    AuthTokens tokens = authService.refresh("old");

    assertEquals("access2", tokens.accessToken());
    assertEquals("new", tokens.refreshToken());
  }

  @Test
  void refreshRejectsAnUnknownOrReusedToken() {
    when(refreshTokenStore.consume("stale")).thenReturn(Optional.empty());

    CommonException exception =
        assertThrows(CommonException.class, () -> authService.refresh("stale"));

    assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, exception.errorCode());
  }

  @Test
  void refreshRejectsAMissingToken() {
    CommonException exception =
        assertThrows(CommonException.class, () -> authService.refresh(null));

    assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, exception.errorCode());
    verifyNoInteractions(refreshTokenStore);
  }

  @Test
  void logoutRevokesTheRefreshToken() {
    authService.logout("token");

    verify(refreshTokenStore).revoke("token");
  }

  @Test
  void logoutWithoutATokenDoesNothing() {
    authService.logout(null);

    verifyNoInteractions(refreshTokenStore);
  }

  private static User user(long id, String email, String passwordHash) throws Exception {
    User user = unverifiedUser(id, email, passwordHash);
    user.markEmailVerified();
    return user;
  }

  private static User unverifiedUser(long id, String email, String passwordHash) throws Exception {
    User user = new User(email, passwordHash, "기록왕", UserRole.USER);
    Field idField = User.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(user, id);
    return user;
  }
}
