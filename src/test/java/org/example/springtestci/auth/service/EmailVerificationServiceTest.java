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

  @BeforeEach
  void setUp() {
    service = new EmailVerificationService(redisTemplate, userRepository, supabase);
  }

  @Test
  void sendLinkAsksSupabaseToMailAndRemembersThePendingUser() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenReturn(true);
    when(supabase.signUp("user@example.com")).thenReturn("sb-1");

    service.sendLink("user@example.com");

    verify(valueOperations).set("auth:email:pending:user@example.com", "sb-1", Duration.ofHours(1));
  }

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

  @Test
  void resendDoesNothingForAnAlreadyVerifiedUser() {
    User verified = new User("user@example.com", "hash", "기록왕", UserRole.USER);
    verified.markEmailVerified();
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(verified));

    service.resend("user@example.com");

    verify(supabase, never()).signUp(anyString());
  }

  @Test
  void resendDoesNothingForAnUnknownEmail() {
    when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

    service.resend("nobody@example.com");

    verify(supabase, never()).signUp(anyString());
  }

  @Test
  void confirmIfVerifiedIsTrueOnceSupabaseConfirmsAndCleansUpTheTemporaryAccount() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("auth:email:pending:user@example.com")).thenReturn("sb-1");
    when(valueOperations.getAndDelete("auth:email:pending:user@example.com")).thenReturn("sb-1");
    when(supabase.isConfirmed("sb-1")).thenReturn(true);

    assertTrue(service.confirmIfVerified("user@example.com"));

    verify(supabase).deleteUser("sb-1");
  }

  @Test
  void confirmIfVerifiedIsFalseWhileTheLinkHasNotBeenClicked() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("auth:email:pending:user@example.com")).thenReturn("sb-1");
    when(supabase.isConfirmed("sb-1")).thenReturn(false);

    assertFalse(service.confirmIfVerified("user@example.com"));
    verify(supabase, never()).deleteUser(anyString());
  }

  @Test
  void confirmIfVerifiedIsFalseWhenNoMailWasRequested() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("auth:email:pending:user@example.com")).thenReturn(null);

    assertFalse(service.confirmIfVerified("user@example.com"));
    verify(supabase, never()).isConfirmed(anyString());
  }

  @Test
  void discardRemovesThePendingTemporaryAccount() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.getAndDelete("auth:email:pending:user@example.com")).thenReturn("sb-1");

    service.discard("user@example.com");

    verify(supabase).deleteUser("sb-1");
  }
}
