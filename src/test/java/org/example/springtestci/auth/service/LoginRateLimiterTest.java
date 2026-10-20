package org.example.springtestci.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.error.RateLimitedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

  private static final String EMAIL_KEY = "auth:login:fail:email:user@example.com";
  private static final String IP_KEY = "auth:login:fail:ip:10.0.0.1";

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private LoginRateLimiter limiter;

  @BeforeEach
  void setUp() {
    limiter = new LoginRateLimiter(redisTemplate);
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  void allowsLoginsBelowTheFailureLimit() {
    when(valueOperations.get(EMAIL_KEY)).thenReturn("4");
    when(valueOperations.get(IP_KEY)).thenReturn("19");

    assertDoesNotThrow(() -> limiter.checkAllowed("user@example.com", "10.0.0.1"));
  }

  @Test
  void rejectsAnEmailThatFailedTooManyTimesAndTellsWhenToRetry() {
    when(valueOperations.get(EMAIL_KEY)).thenReturn("5");
    when(redisTemplate.getExpire(EMAIL_KEY)).thenReturn(600L);

    RateLimitedException exception =
        assertThrows(
            RateLimitedException.class, () -> limiter.checkAllowed("user@example.com", "10.0.0.1"));

    assertEquals(ErrorCode.LOGIN_RATE_LIMITED, exception.errorCode());
    assertEquals(Duration.ofSeconds(600), exception.retryAfter());
  }

  @Test
  void rejectsAnIpThatFailedTooManyTimesAcrossAccounts() {
    when(valueOperations.get(EMAIL_KEY)).thenReturn(null);
    when(valueOperations.get(IP_KEY)).thenReturn("20");
    when(redisTemplate.getExpire(IP_KEY)).thenReturn(null);

    RateLimitedException exception =
        assertThrows(
            RateLimitedException.class, () -> limiter.checkAllowed("user@example.com", "10.0.0.1"));

    assertEquals(Duration.ofMinutes(15), exception.retryAfter());
  }

  @Test
  void startsTheWindowOnTheFirstFailureOnly() {
    when(valueOperations.increment(EMAIL_KEY)).thenReturn(1L);
    when(valueOperations.increment(IP_KEY)).thenReturn(3L);

    limiter.recordFailure("user@example.com", "10.0.0.1");

    verify(redisTemplate).expire(EMAIL_KEY, Duration.ofMinutes(15));
    verify(redisTemplate, never()).expire(IP_KEY, Duration.ofMinutes(15));
  }

  @Test
  void resetClearsOnlyTheEmailCounter() {
    limiter.reset("user@example.com");

    verify(redisTemplate).delete(EMAIL_KEY);
    verify(redisTemplate, never()).delete(IP_KEY);
  }
}
