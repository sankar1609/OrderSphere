package com.ordersphere.inventory.exception;

public class ReservationNotFoundException extends RuntimeException {

  public ReservationNotFoundException(Long orderId) {
    super("No reservation found for orderId: " + orderId);
  }
}
