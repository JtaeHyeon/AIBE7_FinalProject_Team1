package org.example.springtestci.common.security;

import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserIdResolver {

  public long resolve(Jwt jwt) {
    if (jwt == null || jwt.getSubject() == null) {
      throw new CommonException(ErrorCode.UNAUTHORIZED);
    }

    try {
      long userId = Long.parseLong(jwt.getSubject());
      if (userId <= 0) {
        throw new NumberFormatException("user id must be positive");
      }
      return userId;
    } catch (NumberFormatException exception) {
      throw new CommonException(ErrorCode.UNAUTHORIZED);
    }
  }
}
