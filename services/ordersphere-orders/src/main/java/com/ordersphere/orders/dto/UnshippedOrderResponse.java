package com.ordersphere.orders.dto;

import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderStatus;
import java.time.Instant;

/**
 * A paid order's shipment-creation state, as shown to admins. {@code retrying} is false when the
 * saga sweep has given up (shipping rejected it or the attempts ran out) - it needs an admin retry.
 */
public record UnshippedOrderResponse(
    Long orderId,
    String customerUsername,
    OrderStatus status,
    Long paymentId,
    Long shipmentId,
    int shipmentAttempts,
    boolean retrying,
    Instant nextAttemptAt,
    String lastError,
    Instant updatedAt) {

  public static UnshippedOrderResponse from(Order order) {
    return new UnshippedOrderResponse(
        order.getId(),
        order.getCustomerUsername(),
        order.getStatus(),
        order.getPaymentId(),
        order.getShipmentId(),
        order.getShipmentAttempts(),
        order.getShipmentNextAttemptAt() != null,
        order.getShipmentNextAttemptAt(),
        order.getShipmentLastError(),
        order.getUpdatedAt());
  }
}
