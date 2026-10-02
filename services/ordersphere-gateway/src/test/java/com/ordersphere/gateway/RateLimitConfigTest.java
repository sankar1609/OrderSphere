package com.ordersphere.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class RateLimitConfigTest {

  private static MockServerHttpRequest request(String forwardedFor) {
    MockServerHttpRequest.BaseBuilder<?> builder =
        MockServerHttpRequest.get("/x").remoteAddress(new InetSocketAddress("198.51.100.4", 5000));
    if (forwardedFor != null) {
      builder.header("X-Forwarded-For", forwardedFor);
    }
    return builder.build();
  }

  @Test
  void withNoTrustedProxyAClientSuppliedForwardedForIsIgnored() {
    // A client exposed directly to the gateway could otherwise send a new fake address every
    // request and never be rate limited.
    assertThat(RateLimitConfig.clientIp(request("203.0.113.7"), 0)).isEqualTo("198.51.100.4");
  }

  @Test
  void behindOneProxyTheAddressThatProxyAppendedIsUsed() {
    // "1.2.3.4" was supplied by the client; the proxy appended the address it really saw.
    assertThat(RateLimitConfig.clientIp(request("1.2.3.4, 203.0.113.7"), 1))
        .isEqualTo("203.0.113.7");
  }

  @Test
  void behindTwoProxiesTheOuterProxysEntryIsUsed() {
    assertThat(RateLimitConfig.clientIp(request("1.2.3.4, 203.0.113.7, 10.0.0.2"), 2))
        .isEqualTo("203.0.113.7");
  }

  @Test
  void fallsBackToThePeerAddress() {
    assertThat(RateLimitConfig.clientIp(request(null), 1)).isEqualTo("198.51.100.4");
    assertThat(RateLimitConfig.clientIp(request("203.0.113.7"), 2)).isEqualTo("198.51.100.4");
  }
}
