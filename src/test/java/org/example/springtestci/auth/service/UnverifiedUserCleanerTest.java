package org.example.springtestci.auth.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.example.springtestci.auth.entity.User;
import org.example.springtestci.auth.entity.UserRole;
import org.example.springtestci.auth.repository.UserRepository;
import org.example.springtestci.common.audit.BaseTimeEntity;
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

  /** 테스트마다 고정된 시계로 정리기를 만든다. */
  @BeforeEach
  void setUp() {
    cleaner =
        new UnverifiedUserCleaner(
            userRepository,
            emailVerificationService,
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(30));
  }

  /** 제때 인증하지 않은 계정을 지운다. */
  @Test
  void deletesAccountsThatWereNotVerifiedInTime() {
    User expired = new User("old@example.com", "hash", "기록왕", UserRole.USER);
    when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(
            NOW.minus(Duration.ofMinutes(30))))
        .thenReturn(List.of(expired));
    when(emailVerificationService.checkVerification("old@example.com"))
        .thenReturn(VerificationStatus.UNCONFIRMED);

    cleaner.deleteExpired();

    verify(emailVerificationService).discard("old@example.com");
    verify(userRepository).delete(expired);
  }

  /** 링크는 눌렀지만 로그인하지 않은 계정은 지우지 않고 인증 완료로 바꾼다. */
  @Test
  void keepsAndVerifiesAnAccountWhoseLinkWasClickedButNeverLoggedIn() {
    User clicked = new User("clicked@example.com", "hash", "기록왕", UserRole.USER);
    when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(
            NOW.minus(Duration.ofMinutes(30))))
        .thenReturn(List.of(clicked));
    when(emailVerificationService.checkVerification("clicked@example.com"))
        .thenReturn(VerificationStatus.CONFIRMED);

    cleaner.deleteExpired();

    assertTrue(clicked.isEmailVerified());
    verify(userRepository, never()).delete(clicked);
  }

  /** 유예 시간 안에 메일을 다시 보낸 계정은 지우지 않는다. */
  @Test
  void keepsAnAccountWhoseLinkWasResentWithinTheWaitingPeriod() {
    User resent = new User("resent@example.com", "hash", "기록왕", UserRole.USER);
    when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(
            NOW.minus(Duration.ofMinutes(30))))
        .thenReturn(List.of(resent));
    when(emailVerificationService.checkVerification("resent@example.com"))
        .thenReturn(VerificationStatus.UNCONFIRMED);
    when(emailVerificationService.linkSentWithin("resent@example.com", Duration.ofMinutes(30)))
        .thenReturn(true);

    cleaner.deleteExpired();

    verify(userRepository, never()).delete(resent);
    verify(emailVerificationService, never()).discard("resent@example.com");
  }

  /** 대기 기록이 만료된 오래된 계정은 지운다. */
  @Test
  void deletesAnOldAccountWhosePendingRecordHasExpired() throws Exception {
    User old = createdAt("old@example.com", NOW.minus(Duration.ofHours(2)));
    when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(
            NOW.minus(Duration.ofMinutes(30))))
        .thenReturn(List.of(old));
    when(emailVerificationService.checkVerification("old@example.com"))
        .thenReturn(VerificationStatus.EXPIRED);

    cleaner.deleteExpired();

    verify(userRepository).delete(old);
  }

  /** 대기 기록이 없어도 가입한 지 얼마 안 된 계정은 지우지 않는다. */
  @Test
  void keepsARecentAccountWhosePendingRecordIsMissing() throws Exception {
    User recent = createdAt("recent@example.com", NOW.minus(Duration.ofMinutes(40)));
    when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(
            NOW.minus(Duration.ofMinutes(30))))
        .thenReturn(List.of(recent));
    when(emailVerificationService.checkVerification("recent@example.com"))
        .thenReturn(VerificationStatus.EXPIRED);

    cleaner.deleteExpired();

    verify(userRepository, never()).delete(recent);
  }

  /** 가입 시각을 지정한 테스트용 회원을 만든다. */
  private static User createdAt(String email, Instant createdAt) throws Exception {
    User user = new User(email, "hash", "기록왕", UserRole.USER);
    Field field = BaseTimeEntity.class.getDeclaredField("createdAt");
    field.setAccessible(true);
    field.set(user, createdAt);
    return user;
  }

  /** 인증 여부를 알 수 없는 계정은 지우지 않는다. */
  @Test
  void keepsAnAccountWhoseVerificationStatusCannotBeDetermined() {
    User unknown = new User("unknown@example.com", "hash", "기록왕", UserRole.USER);
    when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(
            NOW.minus(Duration.ofMinutes(30))))
        .thenReturn(List.of(unknown));
    when(emailVerificationService.checkVerification("unknown@example.com"))
        .thenReturn(VerificationStatus.UNKNOWN);

    cleaner.deleteExpired();

    assertFalse(unknown.isEmailVerified());
    verify(userRepository, never()).delete(unknown);
    verify(emailVerificationService, never()).discard("unknown@example.com");
  }
}
