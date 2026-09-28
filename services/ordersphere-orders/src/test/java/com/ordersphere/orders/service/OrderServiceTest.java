package com.ordersphere.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.ordersphere.orders.exception.OrderCancellationNotAllowedException;
import com.ordersphere.orders.exception.OrderNotFoundException;
import com.ordersphere.orders.exception.PaymentInitiationException;
import com.ordersphere.orders.exception.ShipmentCreationException;
import com.ordersphere.orders.exception.ShipmentLookupException;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

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
    // By default Inventory reserves everything requested at 10.00 per unit.
    lenient()
        .when(inventoryClient.reserve(any(), anyList(), anyString()))
        .thenAnswer(
            invocation -> {
              List<InventoryClient.ReserveRequest.Item> items = invocation.getArgument(1);
              return new InventoryClient.ReserveResponse(
                  items.stream().map(item -> priced(item.sku(), item.quantity(), "10.00")).toList(),
                  List.of());
            });
  }

  private static InventoryClient.ReserveResponse.LineItem priced(
      String sku, int quantity, String unitPrice) {
    return new InventoryClient.ReserveResponse.LineItem(
        sku, quantity, unitPrice == null ? null : new BigDecimal(unitPrice));
  }

  private CreateOrderRequest requestFor(String sku) {
    return new CreateOrderRequest(
        List.of(new CreateOrderRequest.Item(sku, 3)), "USD", "1 Test Way");
  }

  @Test
  void createOrderAwaitsPaymentWhenReservationAndInitiationSucceed() {
    when(paymentClient.initiate(any(), eq(new BigDecimal("30.00")), eq("USD"), eq("Bearer token")))
        .thenReturn(new PaymentClient.InitiatedPayment(42L, "http://gw/checkout/cs_42"));

    OrderResponse response = orderService.createOrder("alice", requestFor("SKU-1"), "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    assertThat(response.paymentId()).isEqualTo(42L);
    assertThat(response.checkoutUrl()).isEqualTo("http://gw/checkout/cs_42");
    assertThat(response.totalAmount()).isEqualByComparingTo("30.00");
    assertThat(response.currency()).isEqualTo("USD");
    assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("10.00");
    verify(inventoryClient).reserve(any(), anyList(), eq("Bearer token"));
    verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void createOrderChargesInventoryPricesIncludingBackorderedQuantity() {
    when(inventoryClient.reserve(any(), anyList(), anyString()))
        .thenReturn(
            new InventoryClient.ReserveResponse(
                List.of(priced("SKU-A", 2, "4.50"), priced("SKU-B", 1, "12.25")),
                List.of(priced("SKU-B", 2, "12.25"))));
    when(paymentClient.initiate(any(), any(), any(), anyString()))
        .thenReturn(new PaymentClient.InitiatedPayment(42L, "http://gw/checkout/cs_42"));
    CreateOrderRequest request =
        new CreateOrderRequest(
            List.of(
                new CreateOrderRequest.Item("SKU-A", 2), new CreateOrderRequest.Item("SKU-B", 3)),
            "USD",
            "1 Test Way");

    OrderResponse response = orderService.createOrder("alice", request, "Bearer token");

    // 2 x 4.50 + 3 x 12.25 (1 reserved + 2 backordered) = 45.75
    verify(paymentClient)
        .initiate(any(), eq(new BigDecimal("45.75")), eq("USD"), eq("Bearer token"));
    assertThat(response.totalAmount()).isEqualByComparingTo("45.75");
  }

  @Test
  void createOrderCancelsWithoutChargingWhenInventoryReturnsNoPrice() {
    when(inventoryClient.reserve(any(), anyList(), anyString()))
        .thenReturn(
            new InventoryClient.ReserveResponse(List.of(priced("SKU-1", 3, null)), List.of()));

    OrderResponse response = orderService.createOrder("alice", requestFor("SKU-1"), "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryClient).release(any(), eq("Bearer token"));
    verify(paymentClient, never()).initiate(any(), any(), any(), any());
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
    verify(paymentClient, never()).initiate(any(), any(), any(), any());
  }

  @Test
  void createOrderCompensatesInventoryWhenPaymentInitiationFails() {
    doThrow(new PaymentInitiationException("payment provider unavailable"))
        .when(paymentClient)
        .initiate(any(), any(), any(), anyString());

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
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(paymentClient.getStatus(42L, "Bearer service-token"))
        .thenReturn(PaymentClient.PaymentStatus.COMPLETED);
    when(shippingClient.createShipment(10L, "alice", "1 Test Way", "Bearer service-token"))
        .thenReturn(7L);

    orderService.progressAwaitingPayment(10L);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getShipmentId()).isEqualTo(7L);
    verify(inventoryClient).confirm(10L, "Bearer service-token");
    verify(eventPublisher).publishEvent(any(OrderConfirmedEvent.class));
  }

  @Test
  void paymentCompletedStaysAwaitingPaymentWhenInventoryConfirmIsUnavailable() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(paymentClient.getStatus(42L, "Bearer service-token"))
        .thenReturn(PaymentClient.PaymentStatus.COMPLETED);
    doThrow(
            new InventoryReservationException(
                "down",
                HttpServerErrorException.create(
                    HttpStatus.SERVICE_UNAVAILABLE, "", null, null, null)))
        .when(inventoryClient)
        .confirm(10L, "Bearer service-token");

    orderService.progressAwaitingPayment(10L);

    // Left for the next saga sweep to retry - not confirmed, not cancelled, nothing refunded.
    assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    verify(shippingClient, never()).createShipment(any(), any(), any(), any());
    verify(paymentClient, never()).refund(any(), any(), any());
    verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void paymentCompletedRefundsAndCancelsWhenReservationCanNoLongerBeConfirmed() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    doThrow(
            new InventoryReservationException(
                "expired",
                HttpClientErrorException.create(HttpStatus.CONFLICT, "", null, null, null)))
        .when(inventoryClient)
        .confirm(10L, "Bearer service-token");

    orderService.onPaymentEvent(10L, true);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    verify(paymentClient).refund(eq(42L), anyString(), eq("Bearer service-token"));
    verify(shippingClient, never()).createShipment(any(), any(), any(), any());
    verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));
    verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void progressAwaitingPaymentStillConfirmsWhenShipmentCreationFails() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(paymentClient.getStatus(42L, "Bearer service-token"))
        .thenReturn(PaymentClient.PaymentStatus.COMPLETED);
    doThrow(new ShipmentCreationException("boom"))
        .when(shippingClient)
        .createShipment(any(), any(), any(), anyString());

    orderService.progressAwaitingPayment(10L);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getShipmentId()).isNull();
  }

  @Test
  void progressAwaitingPaymentCancelsAndReleasesInventoryWhenPaymentFailed() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(paymentClient.getStatus(42L, "Bearer service-token"))
        .thenReturn(PaymentClient.PaymentStatus.FAILED);

    orderService.progressAwaitingPayment(10L);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryClient).release(10L, "Bearer service-token");
    verify(shippingClient, never()).createShipment(any(), any(), any(), any());
    verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void progressAwaitingPaymentNoOpsWhenStillPending() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(paymentClient.getStatus(42L, "Bearer service-token"))
        .thenReturn(PaymentClient.PaymentStatus.PENDING);

    orderService.progressAwaitingPayment(10L);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    verify(inventoryClient, never()).release(any(), any());
    verify(shippingClient, never()).createShipment(any(), any(), any(), any());
    verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void progressAwaitingPaymentLeavesOrderUnchangedWhenPaymentStatusLookupFails() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(paymentClient.getStatus(42L, "Bearer service-token"))
        .thenThrow(new PaymentInitiationException("circuit breaker open"));

    orderService.progressAwaitingPayment(10L);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    verify(inventoryClient, never()).release(any(), any());
    verify(shippingClient, never()).createShipment(any(), any(), any(), any());
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
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(shippingClient.createShipment(10L, "alice", "1 Test Way", "Bearer service-token"))
        .thenReturn(7L);

    orderService.onPaymentEvent(10L, true);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getShipmentId()).isEqualTo(7L);
    verify(inventoryClient).confirm(10L, "Bearer service-token");
    verify(eventPublisher).publishEvent(any(OrderConfirmedEvent.class));
    verify(paymentClient, never()).getStatus(any(), any());
  }

  @Test
  void onPaymentEventRefundsPaymentThatCompletesAfterOrderWasCancelled() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.CANCELLED);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

    orderService.onPaymentEvent(10L, true);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    verify(paymentClient).refund(eq(42L), anyString(), eq("Bearer service-token"));
    verify(inventoryClient, never()).confirm(any(), any());
    verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));
  }

  @Test
  void checkoutUrlIsOnlyExposedWhileAwaitingPayment() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setCheckoutUrl("http://gw/checkout/cs_1");
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    assertThat(OrderResponse.from(order).checkoutUrl()).isEqualTo("http://gw/checkout/cs_1");

    order.markStatus(OrderStatus.CONFIRMED);
    assertThat(OrderResponse.from(order).checkoutUrl()).isNull();
  }

  @Test
  void onPaymentEventCancelsOrderOnFailure() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

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
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

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
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

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
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

    OrderResponse response = orderService.cancelOrder("alice", false, 10L, "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryClient).release(10L, "Bearer token");
    verify(paymentClient).refund(42L, "Order cancelled by customer", "Bearer token");

    OrderResponse second = orderService.cancelOrder("alice", false, 10L, "Bearer token");
    assertThat(second.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryClient).release(10L, "Bearer token");
  }

  @Test
  void cancelOrderIsRejectedWhenShipmentAlreadyDelivered() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.setShipmentId(7L);
    order.markStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(shippingClient.getStatus(7L, "Bearer token"))
        .thenReturn(ShippingClient.ShipmentStatus.DELIVERED);

    assertThatThrownBy(() -> orderService.cancelOrder("alice", false, 10L, "Bearer token"))
        .isInstanceOf(OrderCancellationNotAllowedException.class);

    verify(inventoryClient, never()).release(any(), any());
    verify(paymentClient, never()).refund(any(), any(), any());
  }

  @Test
  void cancelOrderProceedsWhenShipmentNotYetDelivered() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.setShipmentId(7L);
    order.markStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(shippingClient.getStatus(7L, "Bearer token"))
        .thenReturn(ShippingClient.ShipmentStatus.IN_TRANSIT);

    OrderResponse response = orderService.cancelOrder("alice", false, 10L, "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryClient).release(10L, "Bearer token");
    verify(paymentClient).refund(42L, "Order cancelled by customer", "Bearer token");
  }

  @Test
  void cancelOrderFailsOpenWhenShipmentStatusCannotBeFetched() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.setShipmentId(7L);
    order.markStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(shippingClient.getStatus(7L, "Bearer token"))
        .thenThrow(new ShipmentLookupException("unreachable", new RuntimeException()));

    OrderResponse response = orderService.cancelOrder("alice", false, 10L, "Bearer token");

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void ownershipCheckRejectsAccessToAnotherUsersOrder() {
    when(orderRepository.findByIdAndCustomerUsername(10L, "bob")).thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertThrows(
        OrderNotFoundException.class, () -> orderService.getOrder("bob", false, 10L));
  }
}
