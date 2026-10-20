package org.example.springtestci.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/**
 * HttpOnly Access Token 쿠키를 Authorization 헤더로 바꿔 resource server에 넘긴다. 반드시 CsrfFilter 뒤에 둔다.
 * Spring은 Bearer 토큰이 있는 요청을 CSRF 검사에서 빼므로, 쿠키를 리졸버로 직접 읽게 하면 쿠키 요청도 검사를 건너뛰게 된다.
 */
class CookieBearerTokenFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = cookieToken(request);
    chain.doFilter(token == null ? request : new BearerRequest(request, token), response);
  }

  private static String cookieToken(HttpServletRequest request) {
    if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
      return null;
    }
    // 로그인·갱신·로그아웃은 만료되거나 잘못된 쿠키가 남아 있어도 막히지 않아야 한다.
    if ("POST".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/auth/")) {
      return null;
    }
    Cookie cookie = WebUtils.getCookie(request, SecurityConfig.ACCESS_TOKEN_COOKIE);
    return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
  }

  private static class BearerRequest extends HttpServletRequestWrapper {

    private final String authorization;

    BearerRequest(HttpServletRequest request, String token) {
      super(request);
      this.authorization = "Bearer " + token;
    }

    @Override
    public String getHeader(String name) {
      return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
          ? authorization
          : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
          ? Collections.enumeration(Set.of(authorization))
          : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
      names.add(HttpHeaders.AUTHORIZATION);
      return Collections.enumeration(names);
    }
  }
}
