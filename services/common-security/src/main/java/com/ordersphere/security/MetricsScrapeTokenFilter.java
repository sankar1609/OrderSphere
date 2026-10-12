package com.ordersphere.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards {@code /actuator/prometheus} with a shared scrape token ({@code Authorization: Bearer
 * <token>}). The API gateway routes {@code /{service-id}/**} to every service, so an open metrics
 * endpoint would be public; a user JWT is the wrong key for it (Prometheus has no login, and
 * metrics aren't a customer's business). Without a configured token the endpoint answers nobody.
 *
 * <p>The services' security configs permit the path; this filter does the checking.
 */
public class MetricsScrapeTokenFilter extends OncePerRequestFilter {

  public static final String METRICS_PATH = "/actuator/prometheus";

  private static final String BEARER_PREFIX = "Bearer ";

  private final byte[] scrapeToken;

  public MetricsScrapeTokenFilter(String scrapeToken) {
    this.scrapeToken =
        scrapeToken == null ? new byte[0] : scrapeToken.trim().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().endsWith(METRICS_PATH);
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    if (!allowed(request.getHeader(HttpHeaders.AUTHORIZATION))) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean allowed(String authorization) {
    if (scrapeToken.length == 0
        || authorization == null
        || !authorization.startsWith(BEARER_PREFIX)) {
      return false;
    }
    byte[] presented =
        authorization.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(scrapeToken, presented);
  }
}
