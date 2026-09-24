package com.ordersphere.orders.service;

import com.ordersphere.events.OrderCancelledEvent;
import com.ordersphere.events.OrderConfirmedEvent;
import com.ordersphere.events.OrderCreatedEvent;
import com.ordersphere.orders.client.InventoryClient;
import com.ordersphere.orders.client.PaymentClient;
import com.ordersphere.orders.client.ServiceTokenProvider;
import com.ordersphere.orders.client.ShippingClient;
import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderItem;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private final OrderRepository orderRepository;
  private final InventoryClient inventoryClient;
  private final PaymentClient paymentClient;
  private final ShippingClient shippingClient;
  private final ServiceTokenProvider serviceTokenProvider;
  private final ApplicationEventPublisher eventPublisher;

  public OrderService(
      OrderRepository orderRepository,
      InventoryClient inventoryClient,
      PaymentClient paymentClient,
      ShippingClient shippingClient,
      ServiceTokenProvider serviceTokenProvider,
      ApplicationEventPublisher eventPublisher) {
    this.orderRepository = orderRepository;
    this.inventoryClient = inventoryClient;
    this.paymentClient = paymentClient;
    this.shippingClient = shippingClient;
    this.serviceTokenProvider = serviceTokenProvider;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public OrderResponse createOrder(
      String username, CreateOrderRequest request, String bearerToken) {
    Order order = new Order(username);
    order.setShippingDestination(request.shippingDestination());
    for (CreateOrderRequest.Item item : request.items()) {
      order.addItem(new OrderItem(item.sku(), item.quantity()));
    }
    orderRepository.save(order);

    eventPublisher.publishEvent(
        new OrderCreatedEvent(order.getId(), username, quantitiesBySku(request)));

    List<InventoryClient.ReserveRequest.Item> reserveItems =
        request.items().stream()
            .map(item -> new InventoryClient.ReserveRequest.Item(item.sku(), item.quantity()))
            .toList();

    try {
      inventoryClient.reserve(order.getId(), reserveItems, bearerToken);
    } catch (InventoryReservationException ex) {
      cancelWithReason(order, OrderCancelledEvent.Reason.INVENTORY_UNAVAILABLE);
      return OrderResponse.from(order);
    }

    try {
      Long paymentId =
          paymentClient.initiate(
              order.getId(),
              request.paymentMethodId(),
              request.amount(),
              request.currency(),
              bearerToken);
      order.setPaymentId(paymentId);
      order.markStatus(OrderStatus.AWAITING_PAYMENT);
      orderRepository.save(order);
    } catch (PaymentInitiationException ex) {
      inventoryClient.release(order.getId(), bearerToken);
      cancelWithReason(order, OrderCancelledEvent.Reason.PAYMENT_FAILED);
    }

    return OrderResponse.from(order);
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> listOrders(String username) {
    return orderRepository.findByCustomerUsername(username).stream()
        .map(OrderResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public OrderResponse getOrder(String username, boolean isAdmin, Long orderId) {
    return OrderResponse.from(findOrderOrThrow(username, isAdmin, orderId));
  }

  /**
   * Takes the same row lock as {@link #onPaymentEvent} and {@link #progressAwaitingPayment}, so a
   * customer cancelling right as the saga confirms the order can't race it: whichever gets here
   * first wins, and the loser re-reads the post-lock status instead of acting on a stale one.
   */
  @Transactional
  public OrderResponse cancelOrder(
      String username, boolean isAdmin, Long orderId, String bearerToken) {
    Order order = findOrderForUpdateOrThrow(username, isAdmin, orderId);

    if (order.getStatus() == OrderStatus.CANCELLED) {
      return OrderResponse.from(order);
    }
    if (order.getShipmentId() != null && isDelivered(order.getShipmentId(), bearerToken)) {
      throw new OrderCancellationNotAllowedException(order.getId());
    }
    if (order.getStatus() == OrderStatus.AWAITING_PAYMENT
        || order.getStatus() == OrderStatus.CONFIRMED) {
      inventoryClient.release(order.getId(), bearerToken);
      if (order.getStatus() == OrderStatus.CONFIRMED) {
        paymentClient.refund(order.getPaymentId(), "Order cancelled by customer", bearerToken);
      }
    }

    cancelWithReason(order, OrderCancelledEvent.Reason.CUSTOMER_REQUESTED);

    return OrderResponse.from(order);
  }

  /**
   * Best-effort check: if shipping-service can't be reached, we fail open (allow the cancellation)
   * rather than block a customer's cancel request over a transient dependency outage.
   */
  private boolean isDelivered(Long shipmentId, String bearerToken) {
    try {
      return shippingClient.getStatus(shipmentId, bearerToken)
          == ShippingClient.ShipmentStatus.DELIVERED;
    } catch (ShipmentLookupException ex) {
      log.warn(
          "Unable to verify shipment status for shipmentId {}: {}", shipmentId, ex.getMessage());
      return false;
    }
  }

  /**
   * Re-fetches the order under a row lock before progressing it, so this and {@link
   * #onPaymentEvent} - which can both act on the same AWAITING_PAYMENT order at nearly the same
   * moment (the saga sweep polling vs. the PaymentCompletedEvent listener) - serialize on the order
   * row instead of racing into a duplicate confirmation or a lost update on shipmentId.
   */
  @Transactional
  public void progressAwaitingPayment(Long orderId) {
    Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
    if (order == null || order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
      return;
    }

    PaymentClient.PaymentStatus paymentStatus;
    try {
      paymentStatus =
          paymentClient.getStatus(order.getPaymentId(), serviceTokenProvider.bearerToken());
    } catch (PaymentInitiationException ex) {
      log.warn("Unable to fetch payment status for orderId {}: {}", orderId, ex.getMessage());
      return;
    }

    if (paymentStatus == PaymentClient.PaymentStatus.COMPLETED) {
      handlePaymentCompleted(order);
    } else if (paymentStatus == PaymentClient.PaymentStatus.FAILED) {
      handlePaymentFailed(order);
    }
  }

  /**
   * Reacts to a PaymentCompletedEvent/PaymentFailedEvent consumed directly off the broker (see
   * PaymentEventListener), skipping the paymentClient.getStatus() round-trip that
   * progressAwaitingPayment needs when it's polling blind. Takes the same row lock as
   * progressAwaitingPayment so the two paths serialize instead of racing; guarded the same way, so
   * a redelivered message against an order that's already past AWAITING_PAYMENT is a no-op.
   */
  @Transactional
  public void onPaymentEvent(Long orderId, boolean succeeded) {
    orderRepository
        .findByIdForUpdate(orderId)
        .filter(order -> order.getStatus() == OrderStatus.AWAITING_PAYMENT)
        .ifPresentOrElse(
            order -> {
              if (succeeded) {
                handlePaymentCompleted(order);
              } else {
                handlePaymentFailed(order);
              }
            },
            () ->
                log.debug("Ignoring payment event for orderId {}: not AWAITING_PAYMENT", orderId));
  }

  private void handlePaymentCompleted(Order order) {
    try {
      Long shipmentId =
          shippingClient.createShipment(
              order.getId(),
              order.getCustomerUsername(),
              order.getShippingDestination(),
              serviceTokenProvider.bearerToken());
      order.setShipmentId(shipmentId);
    } catch (ShipmentCreationException ex) {
      log.warn("Shipment creation failed for orderId {}: {}", order.getId(), ex.getMessage());
    }
    order.markStatus(OrderStatus.CONFIRMED);
    orderRepository.save(order);
    eventPublisher.publishEvent(
        new OrderConfirmedEvent(order.getId(), order.getCustomerUsername()));
  }

  private void handlePaymentFailed(Order order) {
    inventoryClient.release(order.getId(), serviceTokenProvider.bearerToken());
    cancelWithReason(order, OrderCancelledEvent.Reason.PAYMENT_FAILED);
  }

  private void cancelWithReason(Order order, OrderCancelledEvent.Reason reason) {
    order.markStatus(OrderStatus.CANCELLED);
    orderRepository.save(order);
    eventPublisher.publishEvent(
        new OrderCancelledEvent(order.getId(), reason, order.getCustomerUsername()));
  }

  private Order findOrderOrThrow(String username, boolean isAdmin, Long orderId) {
    if (isAdmin) {
      return orderRepository
          .findById(orderId)
          .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
    return orderRepository
        .findByIdAndCustomerUsername(orderId, username)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  private Order findOrderForUpdateOrThrow(String username, boolean isAdmin, Long orderId) {
    Order order =
        orderRepository
            .findByIdForUpdate(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    if (!isAdmin && !order.getCustomerUsername().equals(username)) {
      throw new OrderNotFoundException(orderId);
    }
    return order;
  }

  private Map<String, Integer> quantitiesBySku(CreateOrderRequest request) {
    Map<String, Integer> quantities = new HashMap<>();
    for (CreateOrderRequest.Item item : request.items()) {
      quantities.merge(item.sku(), item.quantity(), Integer::sum);
    }
    return quantities;
  }
}
