package com.ordersphere.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String ROLE_CLAIM = "role";

  private final JwtVerifier jwtVerifier;

  public JwtAuthenticationFilter(JwtVerifier jwtVerifier) {
    this.jwtVerifier = jwtVerifier;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    Claims claims = verifiedClaims(extractToken(request));

    if (claims != null) {
      String subject = claims.getSubject();
      Object role = claims.get(ROLE_CLAIM);

      List<GrantedAuthority> authorities =
          role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));

      var authentication = new UsernamePasswordAuthenticationToken(subject, null, authorities);
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    filterChain.doFilter(request, response);
  }

  /** An invalid, expired or foreign-signed token simply leaves the request unauthenticated. */
  private Claims verifiedClaims(String token) {
    if (token == null) {
      return null;
    }
    try {
      return jwtVerifier.verify(token);
    } catch (JwtException | IllegalArgumentException ex) {
      return null;
    }
  }

  private String extractToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }
}
