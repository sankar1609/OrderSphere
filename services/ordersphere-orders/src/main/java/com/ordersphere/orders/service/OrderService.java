package com.ordersphere.orders.service;

import com.ordersphere.events.OrderCancelledEvent;
import com.ordersphere.events.OrderConfirmedEvent;
import com.ordersphere.events.OrderCreatedEvent;
import com.ordersphere.orders.client.InventoryClient;
import com.ordersphere.orders.client.PaymentClient;
import com.ordersphere.orders.client.ServiceTokenProvider;
import com.ordersphere.orders.client.ShippingClient;
import com.ordersphere.orders.config.DownstreamClientErrorPredicate;
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
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);
  private static final DownstreamClientErrorPredicate DOWNSTREAM_CLIENT_ERROR =
      new DownstreamClientErrorPredicate();

  private final OrderRepository orderRepository;
  private final InventoryClient inventoryClient;
  private final PaymentClient paymentClient;
  private final ShippingClient shippingClient;
  private final ServiceTokenProvider serviceTokenProvider;
  private final ApplicationEventPublisher eventPublisher;
  private final CompensationService compensations;

  public OrderService(
      OrderRepository orderRepository,
      InventoryClient inventoryClient,
      PaymentClient paymentClient,
      ShippingClient shippingClient,
      ServiceTokenProvider serviceTokenProvider,
      ApplicationEventPublisher eventPublisher,
      CompensationService compensations) {
    this.orderRepository = orderRepository;
    this.inventoryClient = inventoryClient;
    this.paymentClient = paymentClient;
    this.shippingClient = shippingClient;
    this.serviceTokenProvider = serviceTokenProvider;
    this.eventPublisher = eventPublisher;
    this.compensations = compensations;
  }

  @Transactional
  public OrderResponse createOrder(String username, CreateOrderRequest request) {
    // Downstream calls use orders' own service identity, never the customer's token: the
    // reservation and payment endpoints are service-only, so a customer can't call them directly.
    String bearerToken = serviceTokenProvider.bearerToken();
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

    InventoryClient.ReserveResponse reservation;
    try {
      reservation = inventoryClient.reserve(order.getId(), reserveItems, bearerToken);
    } catch (InventoryReservationException ex) {
      cancelWithReason(order, OrderCancelledEvent.Reason.INVENTORY_UNAVAILABLE);
      return OrderResponse.from(order);
    }

    // The total is always derived from Inventory's prices, never supplied by the client.
    if (!applyPricing(order, reservation)) {
      compensations.releaseInventory(order.getId());
      cancelWithReason(order, OrderCancelledEvent.Reason.INVENTORY_UNAVAILABLE);
      return OrderResponse.from(order);
    }
    order.setCurrency(request.currency());
    orderRepository.save(order);

    try {
      PaymentClient.InitiatedPayment payment =
          paymentClient.initiate(
              order.getId(), username, order.getTotalAmount(), order.getCurrency(), bearerToken);
      order.setPaymentId(payment.id());
      order.setCheckoutUrl(payment.checkoutUrl());
      order.markStatus(OrderStatus.AWAITING_PAYMENT);
      orderRepository.save(order);
    } catch (PaymentInitiationException ex) {
      compensations.releaseInventory(order.getId());
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
  public OrderResponse cancelOrder(String username, boolean isAdmin, Long orderId) {
    Order order = findOrderForUpdateOrThrow(username, isAdmin, orderId);
    String bearerToken = serviceTokenProvider.bearerToken();

    if (order.getStatus() == OrderStatus.CANCELLED) {
      return OrderResponse.from(order);
    }
    if (order.getShipmentId() != null && isDelivered(order.getShipmentId(), bearerToken)) {
      throw new OrderCancellationNotAllowedException(order.getId());
    }
    if (order.getStatus() == OrderStatus.AWAITING_PAYMENT
        || order.getStatus() == OrderStatus.CONFIRMED) {
      compensations.releaseInventory(order.getId());
      if (order.getStatus() == OrderStatus.CONFIRMED) {
        compensations.refundPayment(
            order.getId(), order.getPaymentId(), "Order cancelled by customer");
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
    Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
    if (order == null) {
      log.debug("Ignoring payment event for unknown orderId {}", orderId);
      return;
    }
    if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
      if (succeeded) {
        handlePaymentCompleted(order);
      } else {
        handlePaymentFailed(order);
      }
    } else if (succeeded && order.getStatus() == OrderStatus.CANCELLED) {
      // The customer paid on the still-open checkout page after the order was cancelled (e.g.
      // they cancelled in another tab). Don't keep money for an order we won't fulfil.
      log.warn("Payment completed for already-cancelled orderId {}; refunding", orderId);
      compensations.refundPayment(
          order.getId(), order.getPaymentId(), "Order was cancelled before payment completed");
    } else {
      log.debug("Ignoring payment event for orderId {} in status {}", orderId, order.getStatus());
    }
  }

  private void handlePaymentCompleted(Order order) {
    // Commit the reservation before confirming the order: an unconfirmed reservation is expired by
    // Inventory's sweep, which would put this paid order's stock back on sale.
    try {
      inventoryClient.confirm(order.getId(), serviceTokenProvider.bearerToken());
    } catch (InventoryReservationException ex) {
      if (DOWNSTREAM_CLIENT_ERROR.test(ex)) {
        // Inventory rejected it: the reservation already expired or was released, so the stock is
        // no longer held. Don't ship what we don't have - refund and cancel.
        log.error(
            "Reservation for paid orderId {} can no longer be confirmed ({}); refunding and"
                + " cancelling",
            order.getId(),
            ex.getMessage());
        compensations.refundPayment(
            order.getId(), order.getPaymentId(), "Reserved stock was no longer available");
        cancelWithReason(order, OrderCancelledEvent.Reason.INVENTORY_UNAVAILABLE);
        return;
      }
      // Inventory unavailable: stay AWAITING_PAYMENT so the next saga sweep retries.
      log.warn(
          "Could not confirm reservation for orderId {}, will retry: {}",
          order.getId(),
          ex.getMessage());
      return;
    }

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
    compensations.releaseInventory(order.getId());
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

  /**
   * Stamps each item with Inventory's unit price and sets the order total. Backordered lines are
   * priced too: the customer pays for the full quantity ordered, not just what was in stock.
   * Returns false if any item came back unpriced, in which case the order must not be charged.
   */
  private boolean applyPricing(Order order, InventoryClient.ReserveResponse reservation) {
    Map<String, BigDecimal> unitPriceBySku = new HashMap<>();
    if (reservation != null) {
      Stream.of(reservation.reserved(), reservation.backordered())
          .filter(Objects::nonNull)
          .flatMap(List::stream)
          .filter(line -> line.unitPrice() != null)
          .forEach(line -> unitPriceBySku.put(line.sku(), line.unitPrice()));
    }

    BigDecimal total = BigDecimal.ZERO;
    for (OrderItem item : order.getItems()) {
      BigDecimal unitPrice = unitPriceBySku.get(item.getSku());
      if (unitPrice == null) {
        log.error(
            "Inventory returned no unit price for sku {} on orderId {}",
            item.getSku(),
            order.getId());
        return false;
      }
      item.setUnitPrice(unitPrice);
      total = total.add(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
    }
    order.setTotalAmount(total);
    return true;
  }
}
