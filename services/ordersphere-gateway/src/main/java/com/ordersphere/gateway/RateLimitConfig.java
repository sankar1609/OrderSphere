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

  /**
   * How many reverse proxies / load balancers sit in front of the gateway and append to
   * X-Forwarded-For. 0 (the default, as in docker-compose where the gateway is exposed directly):
   * the header is ignored, since any client can send one with a fresh fake address on every request
   * and never hit the limit.
   */
  private final int trustedProxyHops;

  public RateLimitConfig(@Value("${GATEWAY_TRUSTED_PROXY_HOPS:0}") int trustedProxyHops) {
    this.trustedProxyHops = trustedProxyHops;
  }

  /** The default for RequestRateLimiter filters that don't name a key resolver. */
  @Bean
  @Primary
  public KeyResolver clientIpKeyResolver() {
    return exchange -> Mono.just(clientIp(exchange.getRequest(), trustedProxyHops));
  }

  /**
   * Same client IP, separate bucket for the stricter login/register/refresh/token limit. Redis keys
   * are derived from the resolved key alone, so without the prefix the default and auth limits
   * would share one counter per client.
   */
  @Bean
  public KeyResolver authClientIpKeyResolver() {
    return exchange -> Mono.just("auth:" + clientIp(exchange.getRequest(), trustedProxyHops));
  }

  /**
   * The client address as seen by the outermost trusted proxy. Each proxy appends the address it
   * received the request from, so with N trusted proxies that's the N-th entry from the right of
   * X-Forwarded-For - anything further left was supplied by the client and can't be trusted. With
   * no trusted proxies (or too short a header) it's the TCP peer.
   */
  static String clientIp(ServerHttpRequest request, int trustedProxyHops) {
    String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
    if (trustedProxyHops > 0 && forwarded != null && !forwarded.isBlank()) {
      String[] hops = forwarded.split(",");
      if (hops.length >= trustedProxyHops) {
        return hops[hops.length - trustedProxyHops].trim();
      }
    }
    return Optional.ofNullable(request.getRemoteAddress())
        .map(InetSocketAddress::getAddress)
        .map(address -> address.getHostAddress())
        .orElse("unknown");
  }
}
