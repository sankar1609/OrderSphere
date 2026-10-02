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
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The default per-IP limit, enforced with real Redis in front of a stub backend. */
@Testcontainers
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.gateway.discovery.locator.enabled=false",
      "GATEWAY_RATE_LIMIT_PER_SECOND=1",
      "GATEWAY_RATE_LIMIT_BURST=3"
    })
class GatewayRateLimitIntegrationTest {

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
    // These tests stand in for distinct clients via X-Forwarded-For, i.e. behind one proxy.
    registry.add("GATEWAY_TRUSTED_PROXY_HOPS", () -> "1");
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    // The whole route list must come from one property source (lists aren't merged across sources).
    registry.add("spring.cloud.gateway.routes[0].id", () -> "stub");
    registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/stub/**");
    registry.add(
        "spring.cloud.gateway.routes[0].uri",
        () -> "http://127.0.0.1:" + backend.getAddress().getPort());
  }

  @AfterAll
  static void stopBackend() {
    backend.stop(0);
  }

  @Autowired private WebTestClient client;

  @Test
  void aBurstAboveTheLimitIsRejectedWith429PerClient() {
    List<Integer> statuses = new ArrayList<>();
    EntityExchangeResult<Void> last = null;
    for (int i = 0; i < 6; i++) {
      last =
          client
              .get()
              .uri("/stub/ping")
              .header("X-Forwarded-For", "203.0.113.10")
              .exchange()
              .expectBody(Void.class)
              .returnResult();
      statuses.add(last.getStatus().value());
    }

    assertThat(statuses.subList(0, 3)).containsOnly(200);
    assertThat(statuses).contains(429);
    assertThat(last.getResponseHeaders().getFirst("X-RateLimit-Burst-Capacity")).isEqualTo("3");

    // Another client has its own bucket.
    client
        .get()
        .uri("/stub/ping")
        .header("X-Forwarded-For", "203.0.113.99")
        .exchange()
        .expectStatus()
        .isOk();
  }
}
