package com.ordersphere.orders.exception;

public class PaymentInitiationException extends RuntimeException {

  public PaymentInitiationException(String message) {
    super(message);
  }

  public PaymentInitiationException(String message, Throwable cause) {
    super(message, cause);
  }
}
