package com.ordersphere.orders.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Which downstream answers are worth retrying: outages (5xx), timeouts and throttling (408, 429),
 * and auth failures (401/403 - e.g. the other service couldn't fetch auth-service's keys yet). Any
 * other 4xx is the other service rejecting the request itself, which a retry won't change.
 */
public final class RetryableHttpStatus {

  private RetryableHttpStatus() {}

  public static boolean isRetryable(HttpStatusCode status) {
    int code = status.value();
    return status.is5xxServerError() || code == 401 || code == 403 || code == 408 || code == 429;
  }
}
