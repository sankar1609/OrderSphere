package com.ordersphere.orders.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class RestClientConfigTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void readTimeoutStopsAHungCallBeforeTheServerResponds() throws Exception {
    long readTimeoutMs = 500;
    long serverDelayMs = 5000;

    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/slow",
        exchange -> {
          try {
            Thread.sleep(serverDelayMs);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          exchange.sendResponseHeaders(200, 0);
          exchange.close();
        });
    server.start();

    RestClient.Builder builder =
        new RestClientConfig().loadBalancedRestClientBuilder(2000, readTimeoutMs);
    RestClient client =
        builder.baseUrl("http://localhost:" + server.getAddress().getPort()).build();

    Instant start = Instant.now();

    assertThatThrownBy(() -> client.get().uri("/slow").retrieve().toBodilessEntity())
        .isInstanceOf(ResourceAccessException.class);

    Duration elapsed = Duration.between(start, Instant.now());
    assertThat(elapsed).isLessThan(Duration.ofMillis(serverDelayMs));
  }
}
