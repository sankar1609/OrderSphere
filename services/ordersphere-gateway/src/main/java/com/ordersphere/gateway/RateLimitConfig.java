package com.ordersphere.gateway;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

/**
 * Rate limits are counted per client IP. Requests aren't authenticated at the gateway (each service
 * validates its own JWTs), so the IP is the one key available for every request - including the
 * login/register calls that brute-force protection is mostly about.
 */
@Configuration
public class RateLimitConfig {

  /**
   * Separate limiter beans on purpose: a RedisRateLimiter keeps its per-route settings keyed by
   * route id, so if the default filter and the auth route's filter shared one bean, the default
   * bucket would be evaluated with the strict auth settings on the auth route - and a login burst
   * would starve the client's ordinary API calls.
   */
  @Bean
  @Primary
  public RedisRateLimiter defaultRateLimiter(
      @Value("${GATEWAY_RATE_LIMIT_PER_SECOND:50}") int perSecond,
      @Value("${GATEWAY_RATE_LIMIT_BURST:100}") int burst) {
    return new RedisRateLimiter(perSecond, burst);
  }

  @Bean
  public RedisRateLimiter authRateLimiter(
      @Value("${GATEWAY_AUTH_RATE_LIMIT_PER_SECOND:5}") int perSecond,
      @Value("${GATEWAY_AUTH_RATE_LIMIT_BURST:30}") int burst) {
    return new RedisRateLimiter(perSecond, burst);
  }

  /** The default for RequestRateLimiter filters that don't name a key resolver. */
  @Bean
  @Primary
  public KeyResolver clientIpKeyResolver() {
    return exchange -> Mono.just(clientIp(exchange.getRequest()));
  }

  /**
   * Same client IP, separate bucket for the stricter login/register/refresh/token limit. Redis keys
   * are derived from the resolved key alone, so without the prefix the default and auth limits
   * would share one counter per client.
   */
  @Bean
  public KeyResolver authClientIpKeyResolver() {
    return exchange -> Mono.just("auth:" + clientIp(exchange.getRequest()));
  }

  /**
   * First hop of X-Forwarded-For when a proxy/load balancer sits in front of the gateway, otherwise
   * the TCP peer. Only trust X-Forwarded-For if such a proxy sets it; directly exposed, a client
   * could spoof it to dodge the limit.
   */
  static String clientIp(ServerHttpRequest request) {
    String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return Optional.ofNullable(request.getRemoteAddress())
        .map(InetSocketAddress::getAddress)
        .map(address -> address.getHostAddress())
        .orElse("unknown");
  }
}
