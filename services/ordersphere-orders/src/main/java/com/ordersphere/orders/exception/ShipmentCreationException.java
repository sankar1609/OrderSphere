package com.ordersphere.orders.exception;

/**
 * Shipping-service didn't create the shipment. {@link #isRetryable()} is false only when it
 * rejected the request outright (a 4xx other than 401/403/408/429); outages, an open circuit
 * breaker and timeouts are worth retrying.
 */
public class ShipmentCreationException extends RuntimeException {

  private final boolean retryable;

  public ShipmentCreationException(String message) {
    this(message, null, true);
  }

  public ShipmentCreationException(String message, Throwable cause) {
    this(message, cause, true);
  }

  public ShipmentCreationException(String message, Throwable cause, boolean retryable) {
    super(message, cause);
    this.retryable = retryable;
  }

  public boolean isRetryable() {
    return retryable;
  }
}
