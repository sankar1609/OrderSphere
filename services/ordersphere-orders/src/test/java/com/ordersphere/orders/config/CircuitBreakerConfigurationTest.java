package com.ordersphere.orders.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CircuitBreakerConfigurationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  @Test
  void inventoryServiceCircuitBreakerUsesConfiguredThresholds() {
    assertThresholds(circuitBreakerRegistry.circuitBreaker("inventory-service"), 15);
  }

  @Test
  void paymentServiceCircuitBreakerUsesConfiguredThresholds() {
    assertThresholds(circuitBreakerRegistry.circuitBreaker("payment-service"), 20);
  }

  @Test
  void shippingServiceCircuitBreakerUsesConfiguredThresholds() {
    assertThresholds(circuitBreakerRegistry.circuitBreaker("shipping-service"), 15);
  }

  private void assertThresholds(CircuitBreaker circuitBreaker, int waitDurationInOpenStateSeconds) {
    CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();
    assertThat(config.getSlidingWindowSize()).isEqualTo(20);
    assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
    assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
    assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
        .isEqualTo(waitDurationInOpenStateSeconds * 1000L);
  }
}
