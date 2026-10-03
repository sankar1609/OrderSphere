package com.ordersphere.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "eureka.client.enabled=false")
class OrdersphereGatewayApplicationTests {

  @Autowired private WebTestClient client;

  @Test
  void contextLoads() {}

  @Test
  void everyResponseCarriesItsTraceId() {
    client
        .get()
        .uri("/no-such-route")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .valueMatches("X-Trace-Id", "[0-9a-f]{32}");
  }

  @Test
  void servesOneSwaggerUiListingEveryServicesSpecThroughTheGateway() {
    client.get().uri("/swagger-ui.html").exchange().expectStatus().is3xxRedirection();
    client
        .get()
        .uri("/v3/api-docs/swagger-config")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.urls.length()")
        .isEqualTo(6)
        .jsonPath("$.urls[?(@.name == 'orders')].url")
        .isEqualTo("/ordersphere-orders/v3/api-docs");
  }
}
