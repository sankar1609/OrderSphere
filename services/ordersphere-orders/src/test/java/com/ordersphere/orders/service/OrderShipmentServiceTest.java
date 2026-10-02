package com.ordersphere.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.orders.client.ServiceTokenProvider;
import com.ordersphere.orders.client.ShippingClient;
import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderStatus;
import com.ordersphere.orders.exception.ShipmentCreationException;
import com.ordersphere.orders.exception.ShipmentRetryNotAllowedException;
import com.ordersphere.orders.repository.OrderRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class OrderShipmentServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Mock private OrderRepository orderRepository;
  @Mock private ShippingClient shippingClient;
  @Mock private ServiceTokenProvider serviceTokenProvider;

  private OrderShipmentService service;

  @BeforeEach
  void setUp() {
    service =
        new OrderShipmentService(
            orderRepository,
            shippingClient,
            serviceTokenProvider,
            mock(PlatformTransactionManager.class),
            3,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            Clock.fixed(NOW, ZoneOffset.UTC));
    lenient().when(serviceTokenProvider.bearerToken()).thenReturn("Bearer service");
  }

  private static Order confirmedOrder() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setShippingDestination("1 Test Way");
    order.markStatus(OrderStatus.CONFIRMED);
    return order;
  }

  /** A CONFIRMED order without a shipment whose retry is due, as the sweep would find it. */
  private Order dueOrder() {
    Order order = confirmedOrder();
    order.setShipmentAttempts(1);
    order.setShipmentNextAttemptAt(NOW);
    when(orderRepository.findShipmentDueIds(NOW)).thenReturn(List.of(10L));
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    return order;
  }

  private void shippingFails(boolean retryable) {
    when(shippingClient.createShipment(any(), any(), any(), any()))
        .thenThrow(new ShipmentCreationException("boom", null, retryable));
  }

  @Test
  void successfulAttemptRecordsTheShipment() {
    Order order = confirmedOrder();
    when(shippingClient.createShipment(10L, "alice", "1 Test Way", "Bearer service"))
        .thenReturn(7L);

    service.createShipment(order);

    assertThat(order.getShipmentId()).isEqualTo(7L);
    assertThat(order.getShipmentAttempts()).isEqualTo(1);
    assertThat(order.getShipmentNextAttemptAt()).isNull();
  }

  @Test
  void failedAttemptIsScheduledWithGrowingBackoff() {
    Order order = confirmedOrder();
    shippingFails(true);

    service.createShipment(order);
    assertThat(order.getShipmentNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
    assertThat(order.getShipmentLastError()).isEqualTo("boom");

    service.createShipment(order);
    assertThat(order.getShipmentAttempts()).isEqualTo(2);
    assertThat(order.getShipmentNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
  }

  @Test
  void sweepRetriesDueOrdersUntilTheShipmentIsCreated() {
    Order order = dueOrder();
    when(shippingClient.createShipment(10L, "alice", "1 Test Way", "Bearer service"))
        .thenReturn(7L);

    service.processDue();

    assertThat(order.getShipmentId()).isEqualTo(7L);
    assertThat(order.getShipmentAttempts()).isEqualTo(2);
    assertThat(order.getShipmentNextAttemptAt()).isNull();
    assertThat(order.getShipmentLastError()).isNull();
    verify(orderRepository).save(order);
  }

  @Test
  void givesUpAfterMaxAttempts() {
    Order order = dueOrder();
    shippingFails(true);

    service.processDue();
    order.setShipmentNextAttemptAt(NOW);
    service.processDue();

    assertThat(order.getShipmentAttempts()).isEqualTo(3);
    assertThat(order.getShipmentNextAttemptAt()).isNull();
    assertThat(order.getShipmentLastError()).isEqualTo("boom");
  }

  @Test
  void rejectedByShippingIsGivenUpOnImmediately() {
    Order order = confirmedOrder();
    shippingFails(false);

    service.createShipment(order);

    assertThat(order.getShipmentAttempts()).isEqualTo(1);
    assertThat(order.getShipmentNextAttemptAt()).isNull();
  }

  @Test
  void unreachableAuthServiceIsRetried() {
    Order order = confirmedOrder();
    when(serviceTokenProvider.bearerToken())
        .thenThrow(new ServiceTokenProvider.ServiceTokenException("auth down", null));

    service.createShipment(order);

    assertThat(order.getShipmentNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
    verify(shippingClient, never()).createShipment(any(), any(), any(), any());
  }

  @Test
  void orderCancelledSinceItWasScheduledIsNotShipped() {
    Order order = dueOrder();
    order.markStatus(OrderStatus.CANCELLED);

    service.processDue();

    verify(shippingClient, never()).createShipment(any(), any(), any(), any());
  }

  @Test
  void adminRetryStartsAFreshBudgetEvenAfterGivingUp() {
    Order order = confirmedOrder();
    order.setShipmentAttempts(3);
    order.setShipmentLastError("boom");
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(shippingClient.createShipment(any(), any(), any(), any())).thenReturn(7L);

    var response = service.retry(10L);

    assertThat(response.shipmentId()).isEqualTo(7L);
    assertThat(response.shipmentAttempts()).isEqualTo(1);
    assertThat(response.lastError()).isNull();
  }

  @Test
  void adminRetryOfAnOrderThatAlreadyShippedIsRejected() {
    Order order = confirmedOrder();
    order.setShipmentId(7L);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> service.retry(10L))
        .isInstanceOf(ShipmentRetryNotAllowedException.class);
    verify(shippingClient, never()).createShipment(any(), any(), any(), any());
  }

  @Test
  void backoffDoublesUpToTheCap() {
    assertThat(service.backoff(1)).isEqualTo(Duration.ofSeconds(5));
    assertThat(service.backoff(3)).isEqualTo(Duration.ofSeconds(20));
    assertThat(service.backoff(4)).isEqualTo(Duration.ofSeconds(30));
  }
}
