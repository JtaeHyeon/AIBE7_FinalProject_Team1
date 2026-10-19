package org.example.springtestci.auth.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.example.springtestci.auth.entity.User;
import org.example.springtestci.auth.entity.UserRole;
import org.example.springtestci.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnverifiedUserCleanerTest {

  private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private EmailVerificationService emailVerificationService;

  private UnverifiedUserCleaner cleaner;

  @BeforeEach
  void setUp() {
    cleaner =
        new UnverifiedUserCleaner(
            userRepository,
            emailVerificationService,
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(30));
  }

  @Test
  void deletesAccountsThatWereNotVerifiedInTime() {
    User expired = new User("old@example.com", "hash", "기록왕", UserRole.USER);
    when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(
            NOW.minus(Duration.ofMinutes(30))))
        .thenReturn(List.of(expired));
    when(emailVerificationService.confirmIfVerified("old@example.com")).thenReturn(false);

    cleaner.deleteExpired();

    verify(emailVerificationService).discard("old@example.com");
    verify(userRepository).delete(expired);
  }

  @Test
  void keepsAndVerifiesAnAccountWhoseLinkWasClickedButNeverLoggedIn() {
    User clicked = new User("clicked@example.com", "hash", "기록왕", UserRole.USER);
    when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(
            NOW.minus(Duration.ofMinutes(30))))
        .thenReturn(List.of(clicked));
    when(emailVerificationService.confirmIfVerified("clicked@example.com")).thenReturn(true);

    cleaner.deleteExpired();

    assertTrue(clicked.isEmailVerified());
    verify(userRepository, never()).delete(clicked);
  }
}
