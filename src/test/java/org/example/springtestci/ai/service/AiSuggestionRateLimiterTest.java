package org.example.springtestci.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.example.springtestci.common.error.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class AiSuggestionRateLimiterTest {
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final AiSuggestionRateLimiter limits =
      new AiSuggestionRateLimiter(
          redis, new AiListingProperties(true, "fixture", "gemini-3.5-flash-lite", 20, 3, 30));

  @Test
  void acquiresAnExpiringLeaseAndReleasesOnlyMatchingToken() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenReturn(List.of(1L, 0L));
    var lease = limits.acquire(42);
    assertTrue(lease.key().endsWith(":42"));
    reset(redis);
    limits.release(lease);
    verify(redis).execute(any(RedisScript.class), eq(List.of(lease.key())), eq(lease.token()));
  }

  @Test
  void limitContainsRetryDelay() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenReturn(List.of(0L, 17L));
    var error = assertThrows(RateLimitedException.class, () -> limits.acquire(42));
    assertEquals(ErrorCode.AI_RATE_LIMITED, error.errorCode());
    assertEquals(17, error.retryAfter().toSeconds());
  }

  @Test
  void redisOutageAndUnexpectedResultDenyExternalCalls() {
    assertEquals(
        ErrorCode.AI_UNAVAILABLE,
        assertThrows(CommonException.class, () -> limits.acquire(42)).errorCode());
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenThrow(new IllegalStateException("fixture connection unavailable"));
    assertEquals(
        ErrorCode.AI_UNAVAILABLE,
        assertThrows(CommonException.class, () -> limits.acquire(42)).errorCode());
  }

  @Test
  void releaseFailureDoesNotReplaceOriginalResult() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenThrow(new IllegalStateException());
    assertDoesNotThrow(() -> limits.release(new AiSuggestionRateLimiter.Lease("fixture", "token")));
  }
}
