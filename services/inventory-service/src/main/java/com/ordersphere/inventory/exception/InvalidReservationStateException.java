package com.ordersphere.inventory.exception;

public class InvalidReservationStateException extends RuntimeException {

  public InvalidReservationStateException(String message) {
    super(message);
  }
}
