package com.ordersphere.payment.exception;

public class PaymentMethodNotFoundException extends RuntimeException {

  public PaymentMethodNotFoundException(Long id) {
    super("No payment method found with id: " + id);
  }
}
