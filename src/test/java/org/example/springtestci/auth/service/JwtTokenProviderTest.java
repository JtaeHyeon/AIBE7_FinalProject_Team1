package org.example.springtestci.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.example.springtestci.auth.entity.User;
import org.example.springtestci.auth.entity.UserRole;
import org.example.springtestci.common.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenProviderTest {

  private static final String SECRET = "test-secret-test-secret-test-secret-1234";

  /** HS256 토큰을 발급하고 디코더가 받아들이며, 토큰마다 jti가 다르다. */
  @Test
  void issuesAnHs256TokenThatTheResourceServerDecoderAccepts() throws Exception {
    SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    JwtProperties properties =
        new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(14), true);
    JwtTokenProvider provider =
        new JwtTokenProvider(
            new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties, Clock.systemUTC());

    String token = provider.createAccessToken(user(42L));

    Jwt jwt =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(token);
    assertEquals("42", jwt.getSubject());
    assertNotNull(jwt.getId());
    assertNotEquals(
        jwt.getId(),
        NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
            .decode(provider.createAccessToken(user(42L)))
            .getId());
    assertEquals("USER", jwt.getClaimAsString("scope"));
    assertEquals(Duration.ofMinutes(15), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
    assertEquals(900L, provider.accessTokenExpiresInSeconds());
  }

  /** id를 지정한 테스트용 회원을 만든다. */
  private static User user(long id) throws Exception {
    User user = new User("user@example.com", "hashed", "기록왕", UserRole.USER);
    Field idField = User.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(user, id);
    return user;
  }
}
