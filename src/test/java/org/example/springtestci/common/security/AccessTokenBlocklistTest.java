package org.example.springtestci.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AccessTokenBlocklistTest {

  private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private AccessTokenBlocklist blocklist;

  @BeforeEach
  void setUp() {
    blocklist = new AccessTokenBlocklist(redisTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void blocksTheTokenIdForTheRemainingLifetime() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    blocklist.block(jwt("jti-1", NOW.plusSeconds(600)));

    verify(valueOperations).set("auth:access:blocked:jti-1", "1", Duration.ofSeconds(600));
  }

  @Test
  void ignoresAnExpiredTokenOrOneWithoutAnId() {
    blocklist.block(jwt("jti-1", NOW.minusSeconds(1)));
    blocklist.block(jwt(null, NOW.plusSeconds(600)));

    verifyNoInteractions(redisTemplate);
  }

  @Test
  void reportsOnlyBlockedIdsAsBlocked() {
    when(redisTemplate.hasKey("auth:access:blocked:jti-1")).thenReturn(true);
    when(redisTemplate.hasKey("auth:access:blocked:jti-2")).thenReturn(false);

    assertTrue(blocklist.isBlocked("jti-1"));
    assertFalse(blocklist.isBlocked("jti-2"));
    assertFalse(blocklist.isBlocked(null));
  }

  private static Jwt jwt(String id, Instant expiresAt) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("7")
            .issuedAt(NOW.minusSeconds(60))
            .expiresAt(expiresAt);
    if (id != null) {
      builder.jti(id);
    }
    return builder.build();
  }
}
