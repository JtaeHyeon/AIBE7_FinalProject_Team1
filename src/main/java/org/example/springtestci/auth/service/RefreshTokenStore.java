package org.example.springtestci.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.example.springtestci.common.security.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Refresh Token을 Redis에서 관리한다. 원문 토큰은 저장하지 않고 SHA-256 해시를 키로 쓴다. */
@Component
public class RefreshTokenStore {

  private static final String KEY_PREFIX = "auth:refresh:";
  private static final int TOKEN_BYTES = 32;

  private final StringRedisTemplate redisTemplate;
  private final JwtProperties properties;
  private final SecureRandom secureRandom = new SecureRandom();

  public RefreshTokenStore(StringRedisTemplate redisTemplate, JwtProperties properties) {
    this.redisTemplate = redisTemplate;
    this.properties = properties;
  }

  public String issue(long userId) {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    redisTemplate
        .opsForValue()
        .set(key(token), String.valueOf(userId), properties.refreshTokenTtl());
    return token;
  }

  /** 토큰을 한 번만 쓸 수 있게 조회와 동시에 삭제한다(재사용 방지). */
  public Optional<Long> consume(String token) {
    String userId = redisTemplate.opsForValue().getAndDelete(key(token));
    if (userId == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.parseLong(userId));
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }

  public void revoke(String token) {
    redisTemplate.delete(key(token));
  }

  private static String key(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return KEY_PREFIX + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 not available", exception);
    }
  }
}
