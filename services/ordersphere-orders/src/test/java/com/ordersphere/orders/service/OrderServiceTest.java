package com.ordersphere.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.events.OrderCancelledEvent;
import com.ordersphere.events.OrderConfirmedEvent;
import com.ordersphere.events.OrderCreatedEvent;
import com.ordersphere.orders.client.InventoryClient;
import com.ordersphere.orders.client.PaymentClient;
import com.ordersphere.orders.client.ServiceTokenProvider;
import com.ordersphere.orders.client.ShippingClient;
import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderStatus;
import com.ordersphere.orders.dto.CreateOrderRequest;
import com.ordersphere.orders.dto.OrderResponse;
import com.ordersphere.orders.exception.InventoryReservationException;
import com.ordersphere.orders.exception.OrderNotFoundException;
import com.ordersphere.orders.exception.PaymentInitiationException;
import com.ordersphere.orders.exception.ShipmentCreationException;
import com.ordersphere.orders.repository.OrderRepository;
import java.math.BigDecimal;
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
  @Mock private PaymentClient paymentClient;
  @Mock private ShippingClient shippingClient;
  @Mock private ServiceTokenProvider serviceTokenProvider;
  @Mock private ApplicationEventPublisher eventPublisher;

  private OrderService orderService;

  @BeforeEach
  void setUp() {
    orderService =
        new OrderService(
            orderRepository,
            inventoryClient,
            paymentClient,
            shippingClient,
            serviceTokenProvider,
            eventPublisher);
    lenient().when(serviceTokenProvider.bearerToken()).thenReturn("Bearer service-token");
    lenient()
        .when(orderRepository.save(any(Order.class)))
        .thenAnswer(
            invocation -> {
              Order order = invocation.getArgument(0);
              if (order.getId() == null) {
                order.setId(999L);
              }
              return order;
            });
  }

  private CreateOrderRequest requestFor(String sku) {
    return new CreateOrderRequest(
        List.of(new CreateOrderRequest.Item(sku, 3)),
        5L,
        new BigDecimal("20.00"),
        "USD",
        "1 Test Way");
  }

  @Test
  void createOrderAwaitsPaymentWhenReservationAndInitiationSucceed() {
    when(paymentClient.initiate(
            any(), eq(5L), eq(new BigDecimal("20.00")), eq("USD"), eq("Bearer token")))
        .thenReturn(42L);

    OrderResponse response = orderService.createOrder("alice", requestFor("SKU-1"), "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    assertThat(response.paymentId()).isEqualTo(42L);
    verify(inventoryClient).reserve(any(), anyList(), eq("Bearer token"));
    verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void createOrderCancelsWhenInventoryReservationFails() {
    doThrow(new InventoryReservationException("boom"))
        .when(inventoryClient)
        .reserve(any(), anyList(), anyString());

    OrderResponse response =
        orderService.createOrder("alice", requestFor("SKU-UNKNOWN"), "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
    verify(paymentClient, never()).initiate(any(), any(), any(), any(), any());
  }

  @Test
  void createOrderCompensatesInventoryWhenPaymentInitiationFails() {
    doThrow(new PaymentInitiationException("no such payment method"))
        .when(paymentClient)
        .initiate(any(), any(), any(), any(), anyString());

    OrderResponse response = orderService.createOrder("alice", requestFor("SKU-1"), "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryClient).release(any(), eq("Bearer token"));
    verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void progressAwaitingPaymentConfirmsAndCreatesShipmentWhenPaymentCompleted() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.setShippingDestination("1 Test Way");
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(paymentClient.getStatus(42L, "Bearer service-token"))
        .thenReturn(PaymentClient.PaymentStatus.COMPLETED);
    when(shippingClient.createShipment(10L, "1 Test Way", "Bearer service-token")).thenReturn(7L);

    orderService.progressAwaitingPayment(order);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getShipmentId()).isEqualTo(7L);
    verify(eventPublisher).publishEvent(any(OrderConfirmedEvent.class));
  }

  @Test
  void progressAwaitingPaymentStillConfirmsWhenShipmentCreationFails() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(paymentClient.getStatus(42L, "Bearer service-token"))
        .thenReturn(PaymentClient.PaymentStatus.COMPLETED);
    doThrow(new ShipmentCreationException("boom"))
        .when(shippingClient)
        .createShipment(any(), any(), anyString());

    orderService.progressAwaitingPayment(order);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getShipmentId()).isNull();
  }

  @Test
  void progressAwaitingPaymentCancelsAndReleasesInventoryWhenPaymentFailed() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(paymentClient.getStatus(42L, "Bearer service-token"))
        .thenReturn(PaymentClient.PaymentStatus.FAILED);

    orderService.progressAwaitingPayment(order);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryClient).release(10L, "Bearer service-token");
    verify(shippingClient, never()).createShipment(any(), any(), any());
    verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void progressAwaitingPaymentNoOpsWhenStillPending() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(paymentClient.getStatus(42L, "Bearer service-token"))
        .thenReturn(PaymentClient.PaymentStatus.PENDING);

    orderService.progressAwaitingPayment(order);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    verify(inventoryClient, never()).release(any(), any());
    verify(shippingClient, never()).createShipment(any(), any(), any());
    verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void onPaymentEventConfirmsOrderWithoutCallingPaymentClient() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.setShippingDestination("1 Test Way");
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
    when(shippingClient.createShipment(10L, "1 Test Way", "Bearer service-token")).thenReturn(7L);

    orderService.onPaymentEvent(10L, true);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getShipmentId()).isEqualTo(7L);
    verify(eventPublisher).publishEvent(any(OrderConfirmedEvent.class));
    verify(paymentClient, never()).getStatus(any(), any());
  }

  @Test
  void onPaymentEventCancelsOrderOnFailure() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

    orderService.onPaymentEvent(10L, false);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryClient).release(10L, "Bearer service-token");
    verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void onPaymentEventIgnoresOrderNotAwaitingPayment() {
    Order order = new Order("alice");
    order.setId(10L);
    order.markStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

    orderService.onPaymentEvent(10L, true);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));
  }

  @Test
  void cancelOrderFromAwaitingPaymentReleasesInventoryWithoutRefund() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(orderRepository.findByIdAndCustomerUsername(10L, "alice")).thenReturn(Optional.of(order));

    OrderResponse response = orderService.cancelOrder("alice", false, 10L, "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryClient).release(10L, "Bearer token");
    verify(paymentClient, never()).refund(any(), any(), any());
  }

  @Test
  void cancelOrderFromConfirmedReleasesInventoryAndRefundsAndIsIdempotent() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findByIdAndCustomerUsername(10L, "alice")).thenReturn(Optional.of(order));

    OrderResponse response = orderService.cancelOrder("alice", false, 10L, "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryClient).release(10L, "Bearer token");
    verify(paymentClient).refund(42L, "Order cancelled by customer", "Bearer token");

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
