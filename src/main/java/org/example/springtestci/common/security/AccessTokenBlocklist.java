package org.example.springtestci.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** 로그아웃한 Access Token을 남은 유효 기간 동안 Redis에 두어 사용하지 못하게 한다. 토큰 원문 대신 jti만 저장한다. */
@Component
public class AccessTokenBlocklist {

  private static final String KEY_PREFIX = "auth:access:blocked:";

  private final StringRedisTemplate redisTemplate;
  private final Clock clock;

  /** Redis와 시계를 주입받는다. */
  public AccessTokenBlocklist(StringRedisTemplate redisTemplate, Clock clock) {
    this.redisTemplate = redisTemplate;
    this.clock = clock;
  }

  /** 토큰의 jti를 남은 유효 기간 동안 차단 목록에 올린다. */
  public void block(Jwt jwt) {
    Instant expiresAt = jwt.getExpiresAt();
    if (jwt.getId() == null || expiresAt == null) {
      return;
    }
    Duration remaining = Duration.between(Instant.now(clock), expiresAt);
    if (remaining.isNegative() || remaining.isZero()) {
      return;
    }
    redisTemplate.opsForValue().set(KEY_PREFIX + jwt.getId(), "1", remaining);
  }

  /** jti가 차단 목록에 있는지 확인한다. */
  public boolean isBlocked(String jti) {
    return jti != null && Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
  }
}
