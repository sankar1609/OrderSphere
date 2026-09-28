package com.ordersphere.orders.client;

import com.ordersphere.security.JwtTokenProvider;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ServiceTokenProvider {

  private static final String SERVICE_SUBJECT = "orders-service";

  private final JwtTokenProvider jwtTokenProvider;

  public ServiceTokenProvider(JwtTokenProvider jwtTokenProvider) {
    this.jwtTokenProvider = jwtTokenProvider;
  }

  public String bearerToken() {
    return "Bearer " + jwtTokenProvider.generateToken(SERVICE_SUBJECT, Map.of("role", "SERVICE"));
  }
}
