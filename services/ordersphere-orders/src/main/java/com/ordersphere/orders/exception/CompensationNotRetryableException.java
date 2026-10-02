package com.ordersphere.orders.exception;

import com.ordersphere.orders.domain.Compensation;

/** Only a FAILED compensation can be retried by hand: DONE is finished, PENDING already retries. */
public class CompensationNotRetryableException extends RuntimeException {
  public CompensationNotRetryableException(Long id, Compensation.Status status) {
    super("Compensation " + id + " is " + status + " - only FAILED compensations can be retried");
  }
}
