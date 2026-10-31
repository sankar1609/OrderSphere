package com.ordersphere.gateway;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Refuses a request body too big to be real: the services read a JSON body into memory, so one
 * anonymous 200 MB POST to the login endpoint runs auth-service out of memory. Field-level
 * validation stays in the services.
 *
 * <p>A declared size over the limit is refused before anything is forwarded. A chunked body
 * declares none, so its bytes are counted as they pass and the forwarded request is abandoned at
 * the limit: the service never receives a complete body. Spring Cloud Gateway's own RequestSize
 * filter only reads Content-Length, so it lets chunked bodies through. They can't simply be refused
 * either: reactive HTTP clients send {@code Transfer-Encoding: chunked} on a POST with no body at
 * all.
 *
 * <p>A global filter rather than a WebFilter so that CORS headers are on the refusal, and first in
 * the chain so that a refused request costs no Redis call.
 */
@Component
public class RequestBodyLimitFilter implements GlobalFilter, Ordered {

  private final long maxBytes;

  public RequestBodyLimitFilter(@Value("${GATEWAY_MAX_REQUEST_BODY:1MB}") DataSize maxRequestBody) {
    this.maxBytes = maxRequestBody.toBytes();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    if (request.getHeaders().getContentLength() > maxBytes) {
      return Mono.error(tooLarge());
    }
    return chain.filter(exchange.mutate().request(counting(request)).build());
  }

  private ServerHttpRequest counting(ServerHttpRequest request) {
    return new ServerHttpRequestDecorator(request) {
      @Override
      public Flux<DataBuffer> getBody() {
        AtomicLong seen = new AtomicLong();
        return super.getBody()
            .handle(
                (buffer, sink) -> {
                  if (seen.addAndGet(buffer.readableByteCount()) > maxBytes) {
                    DataBufferUtils.release(buffer);
                    sink.error(tooLarge());
                  } else {
                    sink.next(buffer);
                  }
                });
      }
    };
  }

  private ResponseStatusException tooLarge() {
    return new ResponseStatusException(
        HttpStatus.PAYLOAD_TOO_LARGE, "Request body is larger than " + maxBytes + " bytes");
  }
}
