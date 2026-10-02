package com.ordersphere.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
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
  @Mock private CompensationService compensations;

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
            eventPublisher,
            compensations,
            new OrderShipmentService(
                orderRepository,
                shippingClient,
                serviceTokenProvider,
                mock(PlatformTransactionManager.class),
                20,
                Duration.ofSeconds(5),
                Duration.ofMinutes(10)));
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
                  items.stream()
                      .map(item -> priced(item.sku(), item.quantity(), "10.00"))
                      .toList());
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
    when(paymentClient.initiate(
            any(), eq("alice"), eq(new BigDecimal("30.00")), eq("USD"), eq("Bearer service-token")))
        .thenReturn(new PaymentClient.InitiatedPayment(42L, "http://gw/checkout/cs_42"));

    OrderResponse response = orderService.createOrder("alice", requestFor("SKU-1"));

    assertThat(response.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    assertThat(response.paymentId()).isEqualTo(42L);
    assertThat(response.checkoutUrl()).isEqualTo("http://gw/checkout/cs_42");
    assertThat(response.totalAmount()).isEqualByComparingTo("30.00");
    assertThat(response.currency()).isEqualTo("USD");
    assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("10.00");
    verify(inventoryClient).reserve(any(), anyList(), eq("Bearer service-token"));
    verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void createOrderChargesInventoryPrices() {
    when(inventoryClient.reserve(any(), anyList(), anyString()))
        .thenReturn(
            new InventoryClient.ReserveResponse(
                List.of(priced("SKU-A", 2, "4.50"), priced("SKU-B", 3, "12.25"))));
    when(paymentClient.initiate(any(), eq("alice"), any(), any(), anyString()))
        .thenReturn(new PaymentClient.InitiatedPayment(42L, "http://gw/checkout/cs_42"));
    CreateOrderRequest request =
        new CreateOrderRequest(
            List.of(
                new CreateOrderRequest.Item("SKU-A", 2), new CreateOrderRequest.Item("SKU-B", 3)),
            "USD",
            "1 Test Way");

    OrderResponse response = orderService.createOrder("alice", request);

    // 2 x 4.50 + 3 x 12.25 = 45.75
    verify(paymentClient)
        .initiate(
            any(), eq("alice"), eq(new BigDecimal("45.75")), eq("USD"), eq("Bearer service-token"));
    assertThat(response.totalAmount()).isEqualByComparingTo("45.75");
  }

  @Test
  void anOrderTotallingZeroIsCancelledWithAClearReasonAndNothingCharged() {
    when(inventoryClient.reserve(any(), anyList(), anyString()))
        .thenReturn(new InventoryClient.ReserveResponse(List.of(priced("SKU-1", 3, "0.00"))));

    OrderResponse response = orderService.createOrder("alice", requestFor("SKU-1"));

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(response.cancellationReason())
        .isEqualTo("These items are priced at 0.00 and can't be sold");
    verify(compensations).releaseInventory(any());
    verify(paymentClient, never()).initiate(any(), any(), any(), any(), any());
  }

  @Test
  void createOrderForMoreThanIsInStockIsCancelledWithInventorysReason() {
    doThrow(
            new InventoryReservationException(
                "Inventory reservation failed with status 409",
                null,
                "Not enough stock: SKU-1 (3 requested, 0 available)"))
        .when(inventoryClient)
        .reserve(any(), anyList(), anyString());

    OrderResponse response = orderService.createOrder("alice", requestFor("SKU-1"));

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(response.cancellationReason())
        .isEqualTo("Not enough stock: SKU-1 (3 requested, 0 available)");
    verify(compensations, never()).releaseInventory(any());
    verify(paymentClient, never()).initiate(any(), any(), any(), any(), any());
  }

  @Test
  void createOrderCancelsWithoutChargingWhenInventoryReturnsNoPrice() {
    when(inventoryClient.reserve(any(), anyList(), anyString()))
        .thenReturn(new InventoryClient.ReserveResponse(List.of(priced("SKU-1", 3, null))));

    OrderResponse response = orderService.createOrder("alice", requestFor("SKU-1"));

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(compensations).releaseInventory(any());
    verify(paymentClient, never()).initiate(any(), any(), any(), any(), any());
  }

  @Test
  void createOrderCancelsWhenInventoryReservationFails() {
    doThrow(new InventoryReservationException("boom"))
        .when(inventoryClient)
        .reserve(any(), anyList(), anyString());

    OrderResponse response = orderService.createOrder("alice", requestFor("SKU-UNKNOWN"));

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(response.cancellationReason()).isEqualTo("Some items are unavailable");
    verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
    verify(paymentClient, never()).initiate(any(), any(), any(), any(), any());
  }

  @Test
  void createOrderCompensatesInventoryWhenPaymentInitiationFails() {
    doThrow(new PaymentInitiationException("payment provider unavailable"))
        .when(paymentClient)
        .initiate(any(), eq("alice"), any(), any(), anyString());

    OrderResponse response = orderService.createOrder("alice", requestFor("SKU-1"));

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(compensations).releaseInventory(any());
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
    verify(compensations, never()).refundPayment(any(), any(), any());
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
    verify(compensations).refundPayment(eq(10L), eq(42L), anyString());
    verify(shippingClient, never()).createShipment(any(), any(), any(), any());
    verify(eventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));
    verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
  }

  @Test
  void progressAwaitingPaymentConfirmsAndSchedulesARetryWhenShipmentCreationFails() {
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
    assertThat(order.getShipmentAttempts()).isEqualTo(1);
    assertThat(order.getShipmentNextAttemptAt()).isNotNull();
    assertThat(order.getShipmentLastError()).isEqualTo("boom");
    verify(eventPublisher).publishEvent(any(OrderConfirmedEvent.class));
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
    verify(compensations).releaseInventory(10L);
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
    verify(compensations, never()).releaseInventory(any());
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
    verify(compensations, never()).releaseInventory(any());
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
    verify(compensations).refundPayment(eq(10L), eq(42L), anyString());
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
    verify(compensations).releaseInventory(10L);
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

    OrderResponse response = orderService.cancelOrder("alice", false, 10L);

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(compensations).releaseInventory(10L);
    verify(compensations, never()).refundPayment(any(), any(), any());
  }

  @Test
  void cancelOrderFromConfirmedReleasesInventoryAndRefundsAndIsIdempotent() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.markStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

    OrderResponse response = orderService.cancelOrder("alice", false, 10L);

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(compensations).releaseInventory(10L);
    verify(compensations).refundPayment(10L, 42L, "Order cancelled by customer");

    OrderResponse second = orderService.cancelOrder("alice", false, 10L);
    assertThat(second.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(compensations).releaseInventory(10L);
  }

  @Test
  void cancelOrderIsRejectedWhenShipmentAlreadyDelivered() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.setShipmentId(7L);
    order.markStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(shippingClient.getStatus(7L, "Bearer service-token"))
        .thenReturn(ShippingClient.ShipmentStatus.DELIVERED);

    assertThatThrownBy(() -> orderService.cancelOrder("alice", false, 10L))
        .isInstanceOf(OrderCancellationNotAllowedException.class);

    verify(compensations, never()).releaseInventory(any());
    verify(compensations, never()).refundPayment(any(), any(), any());
  }

  @Test
  void cancelOrderProceedsWhenShipmentNotYetDelivered() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.setShipmentId(7L);
    order.markStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(shippingClient.getStatus(7L, "Bearer service-token"))
        .thenReturn(ShippingClient.ShipmentStatus.IN_TRANSIT);

    OrderResponse response = orderService.cancelOrder("alice", false, 10L);

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(compensations).releaseInventory(10L);
    verify(compensations).refundPayment(10L, 42L, "Order cancelled by customer");
  }

  @Test
  void cancelOrderFailsOpenWhenShipmentStatusCannotBeFetched() {
    Order order = new Order("alice");
    order.setId(10L);
    order.setPaymentId(42L);
    order.setShipmentId(7L);
    order.markStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
    when(shippingClient.getStatus(7L, "Bearer service-token"))
        .thenThrow(new ShipmentLookupException("unreachable", new RuntimeException()));

    OrderResponse response = orderService.cancelOrder("alice", false, 10L);

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void ownershipCheckRejectsAccessToAnotherUsersOrder() {
    when(orderRepository.findByIdAndCustomerUsername(10L, "bob")).thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertThrows(
        OrderNotFoundException.class, () -> orderService.getOrder("bob", false, 10L));
  }
}
