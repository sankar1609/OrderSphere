package com.ordersphere.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A route with the strict credential limit on top of the default one: exhausting the strict limit
 * must not starve the same client's ordinary API calls (regression: both filters once shared the
 * limiter's per-route settings, so the default bucket was held to the strict limit).
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.gateway.discovery.locator.enabled=false",
      "GATEWAY_RATE_LIMIT_PER_SECOND=1",
      "GATEWAY_RATE_LIMIT_BURST=20",
      "GATEWAY_AUTH_RATE_LIMIT_PER_SECOND=1",
      "GATEWAY_AUTH_RATE_LIMIT_BURST=2"
    })
class GatewayAuthRateLimitIntegrationTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  static HttpServer backend;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    backend.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    backend.start();
    String uri = "http://127.0.0.1:" + backend.getAddress().getPort();
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    // Routes as one list from one property source (lists aren't merged across sources).
    registry.add("spring.cloud.gateway.routes[0].id", () -> "credentials");
    registry.add("spring.cloud.gateway.routes[0].uri", () -> uri);
    registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/login");
    registry.add("spring.cloud.gateway.routes[0].filters[0].name", () -> "RequestRateLimiter");
    registry.add(
        "spring.cloud.gateway.routes[0].filters[0].args.key-resolver",
        () -> "#{@authClientIpKeyResolver}");
    registry.add(
        "spring.cloud.gateway.routes[0].filters[0].args.rate-limiter", () -> "#{@authRateLimiter}");
    registry.add("spring.cloud.gateway.routes[1].id", () -> "api");
    registry.add("spring.cloud.gateway.routes[1].uri", () -> uri);
    registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/api/**");
  }

  @AfterAll
  static void stopBackend() {
    backend.stop(0);
  }

  @Autowired private WebTestClient client;

  @Test
  void exhaustingTheCredentialLimitLeavesOrdinaryCallsAlone() {
    List<Integer> logins = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      logins.add(
          client
              .post()
              .uri("/login")
              .header("X-Forwarded-For", "203.0.113.50")
              .exchange()
              .returnResult(Void.class)
              .getStatus()
              .value());
    }
    assertThat(logins.subList(0, 2)).containsOnly(200);
    assertThat(logins).contains(429);

    client
        .get()
        .uri("/api/orders")
        .header("X-Forwarded-For", "203.0.113.50")
        .exchange()
        .expectStatus()
        .isOk();
  }
}
