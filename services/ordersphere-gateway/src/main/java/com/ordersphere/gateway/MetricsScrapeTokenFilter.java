package com.ordersphere.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Guards the gateway's own {@code /actuator/prometheus} with the shared scrape token ({@code
 * Authorization: Bearer <METRICS_SCRAPE_TOKEN>}): the gateway's port is the public one, so its
 * metrics must not be open. Without a configured token the endpoint answers nobody. The services
 * guard theirs the same way (common-security's MetricsScrapeTokenFilter), so a routed {@code
 * /{service-id}/actuator/prometheus} is refused there.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class MetricsScrapeTokenFilter implements WebFilter {

  static final String METRICS_PATH = "/actuator/prometheus";

  private static final String BEARER_PREFIX = "Bearer ";

  private final byte[] scrapeToken;

  public MetricsScrapeTokenFilter(
      @Value("${ordersphere.metrics.scrape-token:${METRICS_SCRAPE_TOKEN:}}") String scrapeToken) {
    this.scrapeToken =
        scrapeToken == null ? new byte[0] : scrapeToken.trim().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!METRICS_PATH.equals(exchange.getRequest().getPath().value())
        || allowed(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))) {
      return chain.filter(exchange);
    }
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    return exchange.getResponse().setComplete();
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
