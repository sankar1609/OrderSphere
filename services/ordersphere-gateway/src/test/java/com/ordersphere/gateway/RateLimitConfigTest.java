package com.ordersphere.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class RateLimitConfigTest {

  @Test
  void usesTheFirstForwardedForHopWhenPresent() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/x")
            .header("X-Forwarded-For", "203.0.113.7, 10.0.0.2")
            .remoteAddress(new InetSocketAddress("10.0.0.2", 5000))
            .build();

    assertThat(RateLimitConfig.clientIp(request)).isEqualTo("203.0.113.7");
  }

  @Test
  void fallsBackToThePeerAddress() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/x")
            .remoteAddress(new InetSocketAddress("198.51.100.4", 5000))
            .build();

    assertThat(RateLimitConfig.clientIp(request)).isEqualTo("198.51.100.4");
  }
}
