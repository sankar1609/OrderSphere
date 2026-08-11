package com.ordersphere.orders.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordersphere.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;

class ServiceTokenProviderTest {

  @Test
  void mintsAdminTokenForOrdersServiceSubject() {
    JwtTokenProvider jwtTokenProvider =
        new JwtTokenProvider("test-secret-key-at-least-32-bytes-long", 3600000);
    ServiceTokenProvider serviceTokenProvider = new ServiceTokenProvider(jwtTokenProvider);

    String bearerToken = serviceTokenProvider.bearerToken();

    assertThat(bearerToken).startsWith("Bearer ");
    String token = bearerToken.substring("Bearer ".length());
    assertThat(jwtTokenProvider.getSubject(token)).isEqualTo("orders-service");
    assertThat(jwtTokenProvider.getClaims(token).get("role", String.class)).isEqualTo("ADMIN");
  }
}
