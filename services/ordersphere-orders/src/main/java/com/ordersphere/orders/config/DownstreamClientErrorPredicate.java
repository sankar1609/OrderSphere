package com.ordersphere.orders.config;

import java.util.function.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

/**
 * Circuit-breaker ignore predicate: a downstream that answered with a 4xx is healthy - it rejected
 * this particular request (unknown SKU, someone else's payment method, ...). Counting those as
 * failures would let a burst of bad customer requests open the breaker and fail every valid order
 * too. 408 and 429 are the exception: they mean the downstream is struggling, so they still count.
 *
 * <p>The clients wrap every {@link RestClientResponseException} in their own exception type, so the
 * cause chain is walked rather than only the top-level exception inspected.
 */
public class DownstreamClientErrorPredicate implements Predicate<Throwable> {

  @Override
  public boolean test(Throwable throwable) {
    for (Throwable t = throwable; t != null; t = t.getCause()) {
      if (t instanceof RestClientResponseException ex) {
        return ex.getStatusCode().is4xxClientError()
            && ex.getStatusCode().value() != HttpStatus.REQUEST_TIMEOUT.value()
            && ex.getStatusCode().value() != HttpStatus.TOO_MANY_REQUESTS.value();
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return false;
  }
}
