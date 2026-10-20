package org.example.springtestci.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class SecurityConfigTest {

  private static final String SECRET = "test-secret-test-secret-test-secret-1234";

  private final JwtProperties properties =
      new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(14), true);
  private final AccessTokenBlocklist blocklist = mock(AccessTokenBlocklist.class);
  private final JwtDecoder decoder = new SecurityConfig().jwtDecoder(properties, blocklist);

  @Test
  void acceptsATokenThatWasNotLoggedOut() {
    assertEquals("7", decoder.decode(token("jti-ok")).getSubject());
  }

  @Test
  void rejectsATokenThatWasLoggedOutEvenThoughItIsStillValid() {
    when(blocklist.isBlocked("jti-blocked")).thenReturn(true);

    assertThrows(JwtValidationException.class, () -> decoder.decode(token("jti-blocked")));
  }

  private String token(String jti) {
    SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .id(jti)
            .subject("7")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .build();
    return new NimbusJwtEncoder(new ImmutableSecret<>(key))
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
