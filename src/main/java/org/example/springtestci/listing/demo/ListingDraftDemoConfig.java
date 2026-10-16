package org.example.springtestci.listing.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** Explicit local fixture profile; never activate this profile in deployment. */
@Configuration
@Profile("sale-draft-demo")
public class ListingDraftDemoConfig {
  @Bean
  @Order(0)
  SecurityFilterChain listingDraftDemoChain(HttpSecurity http) throws Exception {
    http.securityMatcher(
            "/api/v1/listing-draft-products",
            "/api/v1/products/*/listing-draft",
            "/api/v1/products/*/listing-draft/**")
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .addFilterBefore(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                  throws ServletException, IOException {
                if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                  response.sendError(403);
                  return;
                }
                SecurityContextHolder.getContext()
                    .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                            "1", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                chain.doFilter(request, response);
              }
            },
            AnonymousAuthenticationFilter.class);
    return http.build();
  }
}
