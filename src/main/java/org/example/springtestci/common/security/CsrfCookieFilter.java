package org.example.springtestci.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** 인증 과정에서 교체된 CSRF 토큰을 응답 전에 읽어, 다음 브라우저 요청에 쓸 XSRF-TOKEN 쿠키를 발급한다. */
class CsrfCookieFilter extends OncePerRequestFilter {

  /** 응답에 CSRF 쿠키가 실리도록 요청마다 토큰을 읽는다. */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (token != null) {
      token.getToken();
    }
    chain.doFilter(request, response);
  }
}
