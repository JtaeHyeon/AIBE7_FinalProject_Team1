package org.example.springtestci.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserIdResolverTest {

  private final CurrentUserIdResolver resolver = new CurrentUserIdResolver();

  @Test
  void resolvesPositiveUserIdFromJwtSubject() {
    assertEquals(42L, resolver.resolve(jwt("42")));
  }

  @Test
  void rejectsMissingOrInvalidJwtSubject() {
    CommonException missingJwt = assertThrows(CommonException.class, () -> resolver.resolve(null));
    CommonException invalidSubject =
        assertThrows(CommonException.class, () -> resolver.resolve(jwt("not-a-user-id")));

    assertEquals(ErrorCode.UNAUTHORIZED, missingJwt.errorCode());
    assertEquals(ErrorCode.UNAUTHORIZED, invalidSubject.errorCode());
  }

  private Jwt jwt(String subject) {
    return Jwt.withTokenValue("token").header("alg", "HS256").subject(subject).build();
  }
}
