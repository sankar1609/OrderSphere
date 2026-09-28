package com.ordersphere.payment.gateway;

/** The payment provider couldn't be reached or returned an unexpected error. */
public class PaymentGatewayException extends RuntimeException {

  public PaymentGatewayException(String message, Throwable cause) {
    super(message, cause);
  }
}
