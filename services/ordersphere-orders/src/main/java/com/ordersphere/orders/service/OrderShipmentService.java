package com.ordersphere.orders.service;

import com.ordersphere.orders.client.ServiceTokenProvider;
import com.ordersphere.orders.client.ShippingClient;
import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderStatus;
import com.ordersphere.orders.dto.UnshippedOrderResponse;
import com.ordersphere.orders.exception.OrderNotFoundException;
import com.ordersphere.orders.exception.ShipmentCreationException;
import com.ordersphere.orders.exception.ShipmentRetryNotAllowedException;
import com.ordersphere.orders.logging.OrderLogContext;
import com.ordersphere.orders.repository.OrderRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the shipment for a paid order. The first attempt is made as the order is confirmed; if
 * shipping-service can't take it then, the order stays CONFIRMED without a shipment and the saga
 * sweep retries with exponential back-off until it succeeds, shipping rejects it, or the attempts
 * run out (then an admin can retry via /orders/admin/unshipped). Shipping answers a repeated
 * request for the same order with the shipment it already has, so a retry after an ambiguous
 * failure can't ship twice.
 */
@Service
public class OrderShipmentService {

  private static final Logger log = LoggerFactory.getLogger(OrderShipmentService.class);
  private static final int MAX_ERROR_LENGTH = 1000;

  private final OrderRepository orderRepository;
  private final ShippingClient shippingClient;
  private final ServiceTokenProvider serviceTokenProvider;
  private final TransactionTemplate newTransaction;
  private final int maxAttempts;
  private final Duration initialBackoff;
  private final Duration maxBackoff;
  private final Clock clock;

  @Autowired
  public OrderShipmentService(
      OrderRepository orderRepository,
      ShippingClient shippingClient,
      ServiceTokenProvider serviceTokenProvider,
      PlatformTransactionManager transactionManager,
      @Value("${orders.shipment.max-attempts:20}") int maxAttempts,
      @Value("${orders.shipment.initial-backoff:PT5S}") Duration initialBackoff,
      @Value("${orders.shipment.max-backoff:PT10M}") Duration maxBackoff) {
    this(
        orderRepository,
        shippingClient,
        serviceTokenProvider,
        transactionManager,
        maxAttempts,
        initialBackoff,
        maxBackoff,
        Clock.systemUTC());
  }

  OrderShipmentService(
      OrderRepository orderRepository,
      ShippingClient shippingClient,
      ServiceTokenProvider serviceTokenProvider,
      PlatformTransactionManager transactionManager,
      int maxAttempts,
      Duration initialBackoff,
      Duration maxBackoff,
      Clock clock) {
    this.orderRepository = orderRepository;
    this.shippingClient = shippingClient;
    this.serviceTokenProvider = serviceTokenProvider;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.maxAttempts = maxAttempts;
    this.initialBackoff = initialBackoff;
    this.maxBackoff = maxBackoff;
    this.clock = clock;
  }

  /**
   * One attempt to create the order's shipment; on failure records it and schedules the next one.
   * Never throws. The caller holds the order's row lock and saves the order.
   */
  public void createShipment(Order order) {
    order.setShipmentAttempts(order.getShipmentAttempts() + 1);
    try {
      Long shipmentId =
          shippingClient.createShipment(
              order.getId(),
              order.getCustomerUsername(),
              order.getShippingDestination(),
              serviceTokenProvider.bearerToken());
      order.setShipmentId(shipmentId);
      order.setShipmentNextAttemptAt(null);
      order.setShipmentLastError(null);
      if (order.getShipmentAttempts() > 1) {
        log.info(
            "Shipment {} created for orderId {} on attempt {}",
            shipmentId,
            order.getId(),
            order.getShipmentAttempts());
      }
    } catch (ShipmentCreationException ex) {
      recordFailure(order, ex.getMessage(), ex.isRetryable());
    } catch (ServiceTokenProvider.ServiceTokenException ex) {
      recordFailure(order, ex.getMessage(), true);
    }
  }

  /** Saga sweep: retries every order whose shipment creation is due. */
  public void processDue() {
    for (Long orderId : orderRepository.findShipmentDueIds(now())) {
      try (var logContext = OrderLogContext.forOrder(orderId)) {
        newTransaction.executeWithoutResult(status -> retryDue(orderId));
      } catch (RuntimeException ex) {
        log.warn("Shipment retry for orderId {} errored: {}", orderId, ex.getMessage());
      }
    }
  }

  /**
   * Under the same row lock a cancellation takes, so an order cancelled meanwhile is skipped rather
   * than shipped.
   */
  private void retryDue(Long orderId) {
    orderRepository
        .findByIdForUpdate(orderId)
        .filter(OrderShipmentService::awaitingShipment)
        .filter(order -> order.getShipmentNextAttemptAt() != null)
        .ifPresent(
            order -> {
              createShipment(order);
              orderRepository.save(order);
            });
  }

  /** Admin view: paid orders still without a shipment, oldest first. */
  @Transactional(readOnly = true)
  public List<UnshippedOrderResponse> listUnshipped() {
    return orderRepository
        .findByStatusAndShipmentIdIsNullOrderByUpdatedAtAsc(OrderStatus.CONFIRMED)
        .stream()
        .map(UnshippedOrderResponse::from)
        .toList();
  }

  /**
   * Admin action once whatever blocked shipping is fixed: tries again now with a fresh retry
   * budget, whether or not retries were given up on.
   */
  @Transactional
  public UnshippedOrderResponse retry(Long orderId) {
    Order order =
        orderRepository
            .findByIdForUpdate(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    if (!awaitingShipment(order)) {
      throw new ShipmentRetryNotAllowedException(orderId, order.getStatus(), order.getShipmentId());
    }
    log.info("Shipment creation for orderId {} retried by an admin", orderId);
    order.setShipmentAttempts(0);
    createShipment(order);
    orderRepository.save(order);
    return UnshippedOrderResponse.from(order);
  }

  private static boolean awaitingShipment(Order order) {
    return order.getStatus() == OrderStatus.CONFIRMED && order.getShipmentId() == null;
  }

  private void recordFailure(Order order, String error, boolean retryable) {
    order.setShipmentLastError(truncate(error));
    if (!retryable || order.getShipmentAttempts() >= maxAttempts) {
      order.setShipmentNextAttemptAt(null);
      log.error(
          "Giving up on creating a shipment for paid orderId {} after {} attempt(s) - needs manual"
              + " attention: {}",
          order.getId(),
          order.getShipmentAttempts(),
          error);
      return;
    }
    Duration backoff = backoff(order.getShipmentAttempts());
    order.setShipmentNextAttemptAt(now().plus(backoff));
    log.warn(
        "Shipment creation for orderId {} failed (attempt {}), retrying in {}s: {}",
        order.getId(),
        order.getShipmentAttempts(),
        backoff.toSeconds(),
        error);
  }

  /** initial, 2x, 4x, ... capped at maxBackoff. */
  Duration backoff(int attempts) {
    Duration backoff = initialBackoff.multipliedBy(1L << Math.min(attempts - 1, 20));
    return backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff;
  }

  private Instant now() {
    return clock.instant();
  }

  private static String truncate(String error) {
    if (error == null || error.length() <= MAX_ERROR_LENGTH) {
      return error;
    }
    return error.substring(0, MAX_ERROR_LENGTH);
  }
}
