package com.ordersphere.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.events.OrderCancelledEvent;
import com.ordersphere.events.OrderConfirmedEvent;
import com.ordersphere.events.OrderCreatedEvent;
import com.ordersphere.orders.client.InventoryClient;
import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderStatus;
import com.ordersphere.orders.dto.CreateOrderRequest;
import com.ordersphere.orders.dto.OrderResponse;
import com.ordersphere.orders.exception.InventoryReservationException;
import com.ordersphere.orders.exception.OrderNotFoundException;
import com.ordersphere.orders.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private InventoryClient inventoryClient;
  @Mock private ApplicationEventPublisher eventPublisher;

  private OrderService orderService;

  @BeforeEach
  void setUp() {
    orderService = new OrderService(orderRepository, inventoryClient, eventPublisher);
  }

  @Test
  void createOrderConfirmsWhenInventoryReservationSucceeds() {
    CreateOrderRequest request =
        new CreateOrderRequest(List.of(new CreateOrderRequest.Item("SKU-1", 3)));

    OrderResponse response = orderService.createOrder("alice", request, "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(response.customerUsername()).isEqualTo("alice");
    verify(inventoryClient).reserve(any(), anyList(), eq("Bearer token"));
    verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    verify(eventPublisher).publishEvent(any(OrderConfirmedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void createOrderCancelsWhenInventoryReservationFails() {
    CreateOrderRequest request =
        new CreateOrderRequest(List.of(new CreateOrderRequest.Item("SKU-UNKNOWN", 1)));
    doThrow(new InventoryReservationException("boom"))
        .when(inventoryClient)
        .reserve(any(), anyList(), anyString());

    OrderResponse response = orderService.createOrder("alice", request, "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));
    verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
    verify(inventoryClient, never()).release(anyLong(), anyString());
  }

  @Test
  void cancelOrderReleasesInventoryWhenConfirmedAndIsIdempotent() {
    Order order = new Order("alice");
    order.setId(10L);
    order.markStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findByIdAndCustomerUsername(10L, "alice")).thenReturn(Optional.of(order));

    OrderResponse response = orderService.cancelOrder("alice", false, 10L, "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryClient).release(10L, "Bearer token");
    verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));

    OrderResponse second = orderService.cancelOrder("alice", false, 10L, "Bearer token");
    assertThat(second.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryClient).release(10L, "Bearer token");
  }

  @Test
  void ownershipCheckRejectsAccessToAnotherUsersOrder() {
    when(orderRepository.findByIdAndCustomerUsername(10L, "bob")).thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertThrows(
        OrderNotFoundException.class, () -> orderService.getOrder("bob", false, 10L));
  }
}
