package com.ordersphere.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.reactive.server.WebTestClient;

/** The gateway's port is the public one: its Prometheus endpoint needs the scrape token. */
@AutoConfigureObservability(tracing = false)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"eureka.client.enabled=false", "METRICS_SCRAPE_TOKEN=test-scrape-token"})
class GatewayMetricsEndpointTest {

  @Autowired private WebTestClient client;

  @Test
  void refusesAScrapeWithoutTheToken() {
    client.get().uri("/actuator/prometheus").exchange().expectStatus().isUnauthorized();
    client
        .get()
        .uri("/actuator/prometheus")
        .header("Authorization", "Bearer wrong")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void servesMetricsTaggedWithTheApplicationToAScrapeWithTheToken() {
    String body =
        client
            .get()
            .uri("/actuator/prometheus")
            .header("Authorization", "Bearer test-scrape-token")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    assertThat(body)
        .contains("jvm_memory_used_bytes")
        .contains("application=\"ordersphere-gateway\"");
  }

  @Test
  void healthStaysOpen() {
    client.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
  }
}
