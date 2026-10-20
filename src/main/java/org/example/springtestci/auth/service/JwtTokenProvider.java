package org.example.springtestci.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.example.springtestci.auth.entity.User;
import org.example.springtestci.common.security.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private final JwtEncoder jwtEncoder;
  private final JwtProperties properties;
  private final Clock clock;

  public JwtTokenProvider(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  public String createAccessToken(User user) {
    Instant now = Instant.now(clock);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .id(UUID.randomUUID().toString())
            .subject(String.valueOf(user.getId()))
            .issuedAt(now)
            .expiresAt(now.plus(properties.accessTokenTtl()))
            .claim("scope", user.getRole().name())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public long accessTokenExpiresInSeconds() {
    return properties.accessTokenTtl().toSeconds();
  }
}
