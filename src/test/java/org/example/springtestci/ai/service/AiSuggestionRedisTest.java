package org.example.springtestci.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.example.springtestci.common.error.RateLimitedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 명시한 테스트 Redis에서만 실제 Lua 원자성·횟수·만료·토큰 해제를 검증한다. 업무 키를 사용하지 않는다. */
@EnabledIfEnvironmentVariable(named = "AI_TEST_REDIS_PORT", matches = "[0-9]+")
class AiSuggestionRedisTest {
  @Test
  void atomicLimitsAndLeaseReleaseAcrossConcurrentRequests() throws Exception {
    var factory =
        new LettuceConnectionFactory(
            "127.0.0.1", Integer.parseInt(System.getenv("AI_TEST_REDIS_PORT")));
    factory.afterPropertiesSet();
    factory.start();
    var redis = new StringRedisTemplate(factory);
    String prefix = "ai-test:{" + UUID.randomUUID() + "}:";
    var limits =
        new AiSuggestionRateLimiter(
            redis,
            new AiListingProperties(true, "fixture", "gemini-3.5-flash-lite", 1, 3, 4),
            prefix);
    var pool = Executors.newFixedThreadPool(8);
    try {
      var gate = new CountDownLatch(1);
      var calls = new ArrayList<Future<AiSuggestionRateLimiter.Lease>>();
      for (int i = 0; i < 8; i++)
        calls.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    return limits.acquire(1);
                  } catch (RateLimitedException limited) {
                    return null;
                  }
                }));
      gate.countDown();
      var leases = new ArrayList<AiSuggestionRateLimiter.Lease>();
      for (var call : calls) {
        var lease = call.get(5, TimeUnit.SECONDS);
        if (lease != null) leases.add(lease);
      }
      assertEquals(1, leases.size());
      var first = leases.get(0);
      limits.release(first);
      for (int i = 0; i < 2; i++) {
        var lease = limits.acquire(1);
        limits.release(lease);
      }
      assertThrows(RateLimitedException.class, () -> limits.acquire(1));
      var other = limits.acquire(2);
      limits.release(other);
      assertThrows(RateLimitedException.class, () -> limits.acquire(3));
      assertTrue(redis.getExpire(prefix + "global") > 0);
      // 만료 뒤 새 요청이 획득한 잠금을 이전 요청의 finally가 지우지 않는다.
      redis.opsForValue().set(first.key(), "new-request-token", java.time.Duration.ofSeconds(10));
      limits.release(first);
      assertEquals("new-request-token", redis.opsForValue().get(first.key()));
    } finally {
      pool.shutdownNow();
      var keys = redis.keys(prefix + "*");
      if (!keys.isEmpty()) redis.delete(keys);
      factory.destroy();
    }
  }
}
