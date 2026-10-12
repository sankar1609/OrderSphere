package com.ordersphere.orders.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ordersphere.orders.domain.Compensation;
import com.ordersphere.orders.domain.OrderStatus;
import com.ordersphere.orders.repository.CompensationRepository;
import com.ordersphere.orders.repository.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class OrderMetricsTest {

  @Mock private OrderRepository orders;
  @Mock private CompensationRepository compensations;

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @BeforeEach
  void bind() {
    new OrderMetrics(orders, compensations).bindTo(registry);
  }

  @Test
  void reportsOrdersPerStatus() {
    when(orders.countByStatus(OrderStatus.AWAITING_PAYMENT)).thenReturn(3L);
    when(orders.countByStatus(OrderStatus.CONFIRMED)).thenReturn(40L);

    assertThat(gauge("ordersphere.orders", "status", "AWAITING_PAYMENT")).isEqualTo(3.0);
    assertThat(gauge("ordersphere.orders", "status", "CONFIRMED")).isEqualTo(40.0);
  }

  @Test
  void reportsOwedAndStuckCompensations() {
    when(compensations.countByStatus(Compensation.Status.PENDING)).thenReturn(2L);
    when(compensations.countByStatus(Compensation.Status.FAILED)).thenReturn(1L);

    assertThat(gauge("ordersphere.compensations", "status", "PENDING")).isEqualTo(2.0);
    assertThat(gauge("ordersphere.compensations", "status", "FAILED")).isEqualTo(1.0);
  }

  @Test
  void splitsUnshippedPaidOrdersIntoRetryingAndGivenUp() {
    when(orders.countByStatusAndShipmentIdIsNullAndShipmentNextAttemptAtIsNotNull(
            OrderStatus.CONFIRMED))
        .thenReturn(4L);
    when(orders.countByStatusAndShipmentIdIsNullAndShipmentNextAttemptAtIsNull(
            OrderStatus.CONFIRMED))
        .thenReturn(1L);

    assertThat(gauge("ordersphere.orders.unshipped", "state", "retrying")).isEqualTo(4.0);
    assertThat(gauge("ordersphere.orders.unshipped", "state", "gave_up")).isEqualTo(1.0);
  }

  @Test
  void reportsNoValueRatherThanFailingTheScrapeWhenTheDatabaseIsDown() {
    when(compensations.countByStatus(Compensation.Status.FAILED))
        .thenThrow(new DataAccessResourceFailureException("connection refused"));

    assertThat(gauge("ordersphere.compensations", "status", "FAILED")).isNaN();
  }

  private double gauge(String name, String tag, String value) {
    return registry.get(name).tag(tag, value).gauge().value();
  }
}
