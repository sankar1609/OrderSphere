package com.ordersphere.payment.exception;

public class PaymentNotFoundException extends RuntimeException {

  public PaymentNotFoundException(Long id) {
    super("No payment found with id: " + id);
  }
}
