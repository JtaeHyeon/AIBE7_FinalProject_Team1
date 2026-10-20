package org.example.springtestci.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.example.springtestci.auth.entity.User;
import org.example.springtestci.auth.entity.UserRole;
import org.example.springtestci.auth.repository.UserRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private UserRepository userRepository;
  @Mock private SupabaseAuthClient supabase;

  private EmailVerificationService service;

  /** 테스트마다 가짜 구성 요소로 서비스를 만든다. */
  @BeforeEach
  void setUp() {
    service = new EmailVerificationService(redisTemplate, userRepository, supabase);
  }

  /** Supabase에 메일을 요청하고 임시 계정 id를 기억해 둔다. */
  @Test
  void sendLinkAsksSupabaseToMailAndRemembersThePendingUser() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenReturn(true);
    when(supabase.signUp("user@example.com")).thenReturn("sb-1");

    service.sendLink("user@example.com");

    verify(valueOperations).set("auth:email:pending:user@example.com", "sb-1", Duration.ofHours(1));
  }

  /** 임시 계정이 있으면 가입을 다시 하지 않고 메일만 다시 보낸다. */
  @Test
  void sendLinkResendsTheMailInsteadOfSigningUpAgainWhenAPendingAccountExists() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenReturn(true);
    when(valueOperations.get("auth:email:pending:user@example.com")).thenReturn("sb-1");

    service.sendLink("user@example.com");

    verify(supabase).resend("user@example.com");
    verify(supabase, never()).signUp(anyString());
    verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    verify(redisTemplate).expire("auth:email:pending:user@example.com", Duration.ofHours(1));
  }

  /** 재발송 대기 시간 안에는 거부한다. */
  @Test
  void sendLinkWithinTheCooldownIsRejected() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenReturn(false);

    CommonException exception =
        assertThrows(CommonException.class, () -> service.sendLink("user@example.com"));

    assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.errorCode());
    verify(supabase, never()).signUp(anyString());
  }

  /** Supabase 요청이 실패하면 대기 시간을 풀어 다시 시도할 수 있게 한다. */
  @Test
  void sendLinkReleasesTheCooldownWhenSupabaseFails() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenReturn(true);
    when(supabase.signUp("user@example.com"))
        .thenThrow(new CommonException(ErrorCode.EMAIL_SEND_FAILED));

    CommonException exception =
        assertThrows(CommonException.class, () -> service.sendLink("user@example.com"));

    assertEquals(ErrorCode.EMAIL_SEND_FAILED, exception.errorCode());
    verify(redisTemplate).delete("auth:email:cooldown:user@example.com");
  }

  /** 미인증 회원에게 메일을 다시 보낸다. */
  @Test
  void resendMailsAnUnverifiedUser() {
    when(userRepository.findByEmail("user@example.com"))
        .thenReturn(Optional.of(new User("user@example.com", "hash", "기록왕", UserRole.USER)));
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenReturn(true);
    when(supabase.signUp("user@example.com")).thenReturn("sb-1");

    service.resend(" User@Example.com ");

    verify(supabase).signUp("user@example.com");
  }

  /** 이미 인증한 회원에게는 보내지 않는다. */
  @Test
  void resendDoesNothingForAnAlreadyVerifiedUser() {
    User verified = new User("user@example.com", "hash", "기록왕", UserRole.USER);
    verified.markEmailVerified();
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(verified));

    service.resend("user@example.com");

    verify(supabase, never()).signUp(anyString());
  }

  /** 없는 이메일에는 아무 일도 하지 않는다. */
  @Test
  void resendDoesNothingForAnUnknownEmail() {
    when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

    service.resend("nobody@example.com");

    verify(supabase, never()).signUp(anyString());
  }

  /** Supabase가 확인하면 참을 돌려주고 임시 계정을 정리한다. */
  @Test
  void confirmIfVerifiedIsTrueOnceSupabaseConfirmsAndCleansUpTheTemporaryAccount() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("auth:email:pending:user@example.com")).thenReturn("sb-1");
    when(valueOperations.getAndDelete("auth:email:pending:user@example.com")).thenReturn("sb-1");
    when(supabase.confirmationStatus("sb-1")).thenReturn(VerificationStatus.CONFIRMED);

    assertTrue(service.confirmIfVerified("user@example.com"));

    verify(supabase).deleteUser("sb-1");
  }

  /** 링크를 누르기 전에는 거짓을 돌려준다. */
  @Test
  void confirmIfVerifiedIsFalseWhileTheLinkHasNotBeenClicked() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("auth:email:pending:user@example.com")).thenReturn("sb-1");
    when(supabase.confirmationStatus("sb-1")).thenReturn(VerificationStatus.UNCONFIRMED);

    assertFalse(service.confirmIfVerified("user@example.com"));
    verify(supabase, never()).deleteUser(anyString());
  }

  /** 메일을 요청한 적이 없으면 거짓을 돌려준다. */
  @Test
  void confirmIfVerifiedIsFalseWhenNoMailWasRequested() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("auth:email:pending:user@example.com")).thenReturn(null);

    assertFalse(service.confirmIfVerified("user@example.com"));
    verify(supabase, never()).confirmationStatus(anyString());
  }

  /** 대기 기록이 사라지면 만료 상태다. */
  @Test
  void checkVerificationIsExpiredOnceThePendingRecordIsGone() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("auth:email:pending:user@example.com")).thenReturn(null);

    assertEquals(VerificationStatus.EXPIRED, service.checkVerification("user@example.com"));
  }

  /** Supabase를 조회하지 못하면 알 수 없음 상태다. */
  @Test
  void checkVerificationIsUnknownWhenSupabaseCannotBeQueried() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("auth:email:pending:user@example.com")).thenReturn("sb-1");
    when(supabase.confirmationStatus("sb-1")).thenReturn(VerificationStatus.UNKNOWN);

    assertEquals(VerificationStatus.UNKNOWN, service.checkVerification("user@example.com"));
  }

  /** 대기 기록이 최근에 갱신된 동안만 최근 발송으로 본다. */
  @Test
  void linkSentWithinIsTrueOnlyWhileTheRecordWasRefreshedRecently() {
    when(redisTemplate.getExpire("auth:email:pending:user@example.com"))
        .thenReturn(2400L, 1200L, null);

    // 남은 40분 = 20분 전에 보냄, 남은 20분 = 40분 전에 보냄
    assertTrue(service.linkSentWithin("user@example.com", Duration.ofMinutes(30)));
    assertFalse(service.linkSentWithin("user@example.com", Duration.ofMinutes(30)));
    assertFalse(service.linkSentWithin("user@example.com", Duration.ofMinutes(30)));
  }

  /** 대기 중인 임시 계정을 지운다. */
  @Test
  void discardRemovesThePendingTemporaryAccount() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.getAndDelete("auth:email:pending:user@example.com")).thenReturn("sb-1");

    service.discard("user@example.com");

    verify(supabase).deleteUser("sb-1");
  }
}
