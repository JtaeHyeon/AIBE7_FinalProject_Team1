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

    service.sendLink(" User@Example.com ");

    verify(valueOperations)
        .set("auth:email:pending:user@example.com", "sb-1", Duration.ofMinutes(60));
  }

  @Test
  void sendLinkRejectsAnAlreadyRegisteredEmail() {
    when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

    CommonException exception =
        assertThrows(CommonException.class, () -> service.sendLink("user@example.com"));

    assertEquals(ErrorCode.DUPLICATE_EMAIL, exception.errorCode());
    verify(supabase, never()).signUp(anyString());
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
  void isVerifiedMarksTheEmailOnceSupabaseConfirmsIt() {
    when(redisTemplate.hasKey("auth:email:verified:user@example.com")).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("auth:email:pending:user@example.com")).thenReturn("sb-1");
    when(supabase.isConfirmed("sb-1")).thenReturn(true);

    assertTrue(service.isVerified(" User@Example.com "));

    verify(valueOperations)
        .set("auth:email:verified:user@example.com", "1", Duration.ofMinutes(30));
  }

  @Test
  void isVerifiedIsFalseWhileTheLinkHasNotBeenClicked() {
    when(redisTemplate.hasKey("auth:email:verified:user@example.com")).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("auth:email:pending:user@example.com")).thenReturn("sb-1");
    when(supabase.isConfirmed("sb-1")).thenReturn(false);

    assertFalse(service.isVerified("user@example.com"));
    verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void isVerifiedIsFalseWhenNoMailWasRequested() {
    when(redisTemplate.hasKey("auth:email:verified:user@example.com")).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("auth:email:pending:user@example.com")).thenReturn(null);

    assertFalse(service.isVerified("user@example.com"));
    verify(supabase, never()).isConfirmed(anyString());
  }

  @Test
  void isVerifiedIsTrueWithoutCallingSupabaseWhenAlreadyMarked() {
    when(redisTemplate.hasKey("auth:email:verified:user@example.com")).thenReturn(true);

    assertTrue(service.isVerified("user@example.com"));
    verify(supabase, never()).isConfirmed(anyString());
  }

  @Test
  void consumeVerifiedUsesTheMarkOnceAndCleansUpTheTemporaryAccount() {
    when(redisTemplate.delete("auth:email:verified:user@example.com")).thenReturn(true);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.getAndDelete("auth:email:pending:user@example.com")).thenReturn("sb-1");

    assertTrue(service.consumeVerified("user@example.com"));

    verify(supabase).deleteUser("sb-1");
  }

  @Test
  void consumeVerifiedIsFalseWithoutAMark() {
    when(redisTemplate.delete("auth:email:verified:user@example.com")).thenReturn(false);

    assertFalse(service.consumeVerified("user@example.com"));
    verify(supabase, never()).deleteUser(anyString());
  }
}
