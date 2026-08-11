package com.ordersphere.shipping.exception;

public class InvalidShipmentStateException extends RuntimeException {

  public InvalidShipmentStateException(String message) {
    super(message);
  }
}
