package com.ordersphere.orders.exception;

import com.ordersphere.orders.domain.OrderStatus;

public class ShipmentRetryNotAllowedException extends RuntimeException {

  public ShipmentRetryNotAllowedException(Long orderId, OrderStatus status, Long shipmentId) {
    super(
        shipmentId != null
            ? "Order " + orderId + " already has shipment " + shipmentId
            : "Order " + orderId + " is " + status + " - only CONFIRMED orders are shipped");
  }
}
