package com.ordersphere.orders.exception;

public class OrderCancellationNotAllowedException extends RuntimeException {

  public OrderCancellationNotAllowedException(Long orderId) {
    super("Order " + orderId + " has already been delivered and can no longer be cancelled");
  }
}
