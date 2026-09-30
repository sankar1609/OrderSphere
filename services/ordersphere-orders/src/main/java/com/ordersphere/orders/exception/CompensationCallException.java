package com.ordersphere.orders.exception;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * A refund or stock release call didn't succeed. {@link #isRetryable()} says whether trying again
 * can help: outages, timeouts, throttling and auth failures (e.g. a JWKS that couldn't be fetched
 * yet) can; a request the other service rejects outright (409, 400, ...) can't.
 */
public class CompensationCallException extends RuntimeException {

  private final boolean retryable;
  private final Integer status;

  public CompensationCallException(
      String message, Throwable cause, boolean retryable, Integer status) {
    super(message, cause);
    this.retryable = retryable;
    this.status = status;
  }

  public static CompensationCallException from(String action, RestClientException ex) {
    if (ex instanceof RestClientResponseException response) {
      int status = response.getStatusCode().value();
      boolean retryable =
          response.getStatusCode().is5xxServerError()
              || status == 401
              || status == 403
              || status == 408
              || status == 429;
      return new CompensationCallException(
          action + " failed with HTTP " + status, ex, retryable, status);
    }
    return new CompensationCallException(action + " failed: " + ex.getMessage(), ex, true, null);
  }

  public boolean isRetryable() {
    return retryable;
  }

  /** HTTP status the other service answered with, or null if it wasn't reached. */
  public Integer getStatus() {
    return status;
  }
}
