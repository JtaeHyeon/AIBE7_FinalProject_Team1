package org.example.springtestci.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** CSRF 토큰은 사용될 때 쿠키로 발급되므로, 화면을 처음 받을 때도 XSRF-TOKEN 쿠키가 내려가도록 매 요청마다 토큰을 읽는다. */
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
