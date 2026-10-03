package com.ordersphere.gateway;

import io.micrometer.tracing.handler.TracingObservationHandler;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Puts the trace id on every response as {@code X-Trace-Id} - including the ones the gateway
 * answers itself (429 rate limited, 404 no route, 503 no instance). Set just before the response
 * commits, replacing the identical header a downstream service already returned, so there's one.
 */
@Component
public class TraceIdResponseHeaderFilter implements WebFilter {

  static final String HEADER = "X-Trace-Id";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerRequestObservationContext.findCurrent(exchange.getAttributes())
        .map(
            context ->
                context.<TracingObservationHandler.TracingContext>get(
                    TracingObservationHandler.TracingContext.class))
        .map(TracingObservationHandler.TracingContext::getSpan)
        .map(span -> span.context().traceId())
        .ifPresent(
            traceId ->
                exchange
                    .getResponse()
                    .beforeCommit(
                        () -> {
                          exchange.getResponse().getHeaders().set(HEADER, traceId);
                          return Mono.empty();
                        }));
    return chain.filter(exchange);
  }
}
