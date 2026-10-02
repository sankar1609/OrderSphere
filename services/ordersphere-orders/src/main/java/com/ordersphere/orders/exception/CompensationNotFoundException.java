package com.ordersphere.orders.exception;

public class CompensationNotFoundException extends RuntimeException {
  public CompensationNotFoundException(Long id) {
    super("No compensation found with id: " + id);
  }
}
