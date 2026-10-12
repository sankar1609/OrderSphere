package com.ordersphere.orders.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ordersphere.events.OrderCreatedEvent;
import com.ordersphere.security.testing.TestJwtIssuer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * /actuator/prometheus on a service: only a scrape carrying the shared token gets in (the gateway
 * would otherwise make it public), and it serves the business metrics next to the built-in ones.
 */
@Testcontainers
@AutoConfigureObservability(tracing = false)
@SpringBootTest(
    properties = {"eureka.client.enabled=false", "METRICS_SCRAPE_TOKEN=test-scrape-token"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class MetricsEndpointIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationEventPublisher events;

  @Test
  void refusesAScrapeWithoutTheToken() throws Exception {
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/actuator/prometheus").header("Authorization", "Bearer wrong"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aUsersTokenIsNotTheScrapeToken() throws Exception {
    mockMvc
        .perform(
            get("/actuator/prometheus")
                .header("Authorization", TestJwtIssuer.bearer("admin", "ADMIN")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void servesBusinessAndBuiltInMetricsToAScrapeWithTheToken() throws Exception {
    events.publishEvent(new OrderCreatedEvent(1L, "alice", Map.of("SKU-1", 1)));

    String body =
        mockMvc
            .perform(
                get("/actuator/prometheus").header("Authorization", "Bearer test-scrape-token"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .contains("application=\"ordersphere-orders\"")
        .contains("jvm_memory_used_bytes")
        .containsPattern("ordersphere_events_total\\{[^}]*type=\"OrderCreatedEvent\"[^}]*} 1\\.0")
        .containsPattern("ordersphere_orders\\{[^}]*status=\"CONFIRMED\"")
        .containsPattern("ordersphere_compensations\\{[^}]*status=\"FAILED\"[^}]*} 0\\.0")
        .containsPattern("ordersphere_orders_unshipped\\{[^}]*state=\"gave_up\"");
  }

  @Test
  void healthStaysOpen() throws Exception {
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }
}
