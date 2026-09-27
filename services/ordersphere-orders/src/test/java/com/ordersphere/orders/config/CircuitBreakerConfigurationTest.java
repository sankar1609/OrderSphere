package com.ordersphere.orders.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordersphere.orders.exception.InventoryReservationException;
import com.ordersphere.orders.exception.PaymentInitiationException;
import com.ordersphere.orders.exception.ShipmentLookupException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
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

  @Test
  void downstreamClientErrorsDoNotCountAsFailures() {
    for (Map.Entry<String, Function<Throwable, RuntimeException>> breaker : BREAKERS.entrySet()) {
      CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(breaker.getKey());
      circuitBreaker.reset();

      for (int i = 0; i < 20; i++) {
        circuitBreaker.onError(
            0, TimeUnit.MILLISECONDS, breaker.getValue().apply(status(HttpStatus.NOT_FOUND)));
      }

      assertThat(circuitBreaker.getState()).as(breaker.getKey()).isEqualTo(State.CLOSED);
      assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls())
          .as(breaker.getKey())
          .isZero();
    }
  }

  @Test
  void downstreamOutagesStillOpenTheBreaker() {
    for (Map.Entry<String, Function<Throwable, RuntimeException>> breaker : BREAKERS.entrySet()) {
      for (Throwable cause :
          List.of(
              status(HttpStatus.SERVICE_UNAVAILABLE),
              status(HttpStatus.TOO_MANY_REQUESTS),
              new ResourceAccessException("Connection refused"))) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(breaker.getKey());
        circuitBreaker.reset();

        for (int i = 0; i < 10; i++) {
          circuitBreaker.onError(0, TimeUnit.MILLISECONDS, breaker.getValue().apply(cause));
        }

        assertThat(circuitBreaker.getState())
            .as(breaker.getKey() + " / " + cause.getMessage())
            .isEqualTo(State.OPEN);
        circuitBreaker.reset();
      }
    }
  }

  /** Each breaker only records its own client's exception type, so test each with that type. */
  private static final Map<String, Function<Throwable, RuntimeException>> BREAKERS =
      Map.of(
          "inventory-service", cause -> new InventoryReservationException("x", cause),
          "payment-service", cause -> new PaymentInitiationException("x", cause),
          "shipping-service", cause -> new ShipmentLookupException("x", cause));

  private static RestClientResponseException status(HttpStatus status) {
    return new RestClientResponseException(
        status.toString(), status, status.name(), null, null, null);
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
