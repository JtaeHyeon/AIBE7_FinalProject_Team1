package org.example.springtestci.ai.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.error.RateLimitedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 같은 Redis를 쓰는 서버 전체에서 회원별 동시 1개·분당 횟수와 전체 분당 횟수를 원자적으로 제한한다. */
@Component
public class AiSuggestionRateLimiter {
  // 횟수 검사·증가·동시 실행 잠금을 Redis에서 한 번에 처리해 여러 서버의 동시 요청도 제한한다.
  private static final DefaultRedisScript<List> ACQUIRE =
      new DefaultRedisScript<>(
          """
      local ttl = redis.call('TTL', KEYS[3])
      if ttl >= 0 then return {0, math.max(1, ttl)} end
      local user = tonumber(redis.call('GET', KEYS[1]) or '0')
      local total = tonumber(redis.call('GET', KEYS[2]) or '0')
      if user >= tonumber(ARGV[1]) then return {0, math.max(1, redis.call('TTL', KEYS[1]))} end
      if total >= tonumber(ARGV[2]) then return {0, math.max(1, redis.call('TTL', KEYS[2]))} end
      if redis.call('INCR', KEYS[1]) == 1 then redis.call('EXPIRE', KEYS[1], 60) end
      if redis.call('INCR', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], 60) end
      redis.call('SET', KEYS[3], ARGV[3], 'EX', ARGV[4])
      return {1, 0}
      """,
          List.class);
  // 이번 호출의 토큰이 맞을 때만 잠금을 지운다. 늦게 끝난 이전 호출이 새 잠금을 지우지 못하게 한다.
  private static final DefaultRedisScript<Long> RELEASE =
      new DefaultRedisScript<>(
          """
      if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
      return 0
      """,
          Long.class);
  private final StringRedisTemplate redis;
  private final AiListingProperties properties;
  private final String prefix;

  @Autowired
  public AiSuggestionRateLimiter(StringRedisTemplate redis, AiListingProperties properties) {
    this(redis, properties, "ai:{listing-suggestion}:");
  }

  AiSuggestionRateLimiter(
      StringRedisTemplate redis, AiListingProperties properties, String prefix) {
    this.redis = redis;
    this.properties = properties;
    this.prefix = prefix;
  }

  /** 사용자·전체 분당 한도를 검사하고, 같은 사용자의 AI 호출이 동시에 실행되지 않도록 실행권을 받는다. */
  public Lease acquire(long userId) {
    String lock = prefix + "inflight:" + userId;
    String token = UUID.randomUUID().toString();
    try {
      List<?> result =
          redis.execute(
              ACQUIRE,
              List.of(prefix + "user:" + userId, prefix + "global", lock),
              String.valueOf(properties.userRequestsPerMinute()),
              String.valueOf(properties.globalRequestsPerMinute()),
              token,
              String.valueOf(properties.timeoutSeconds() + 15));
      if (result == null || result.size() != 2) throw new CommonException(ErrorCode.AI_UNAVAILABLE);
      if (((Number) result.get(0)).longValue() != 1)
        throw new RateLimitedException(
            ErrorCode.AI_RATE_LIMITED,
            Duration.ofSeconds(Math.max(1, ((Number) result.get(1)).longValue())));
      return new Lease(lock, token);
    } catch (CommonException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      // Redis 장애 때 무제한 외부 호출로 바뀌지 않는다.
      throw new CommonException(ErrorCode.AI_UNAVAILABLE);
    }
  }

  /** 사용이 끝난 동시 실행 잠금만 반납한다. 이미 사용한 분당 요청 횟수는 되돌리지 않는다. */
  public void release(Lease lease) {
    try {
      redis.execute(RELEASE, List.of(lease.key()), lease.token());
    } catch (RuntimeException ignored) {
      // 해제 실패 시 TTL로 회복한다. 원래 결과·오류를 가리지 않는다.
    }
  }

  public record Lease(String key, String token) {}
}
