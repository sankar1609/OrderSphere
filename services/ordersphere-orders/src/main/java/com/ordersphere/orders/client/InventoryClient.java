package com.ordersphere.orders.client;

import com.ordersphere.orders.exception.CompensationCallException;
import com.ordersphere.orders.exception.InventoryReservationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class InventoryClient {

  private final RestClient restClient;

  public InventoryClient(RestClient.Builder loadBalancedRestClientBuilder) {
    this.restClient = loadBalancedRestClientBuilder.baseUrl("http://inventory-service").build();
  }

  @CircuitBreaker(name = "inventory-service", fallbackMethod = "reserveFallback")
  public ReserveResponse reserve(
      Long orderId, List<ReserveRequest.Item> items, String bearerToken) {
    try {
      return restClient
          .post()
          .uri("/inventory/reservations")
          .header(HttpHeaders.AUTHORIZATION, bearerToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(new ReserveRequest(orderId, items))
          .retrieve()
          .body(ReserveResponse.class);
    } catch (RestClientResponseException ex) {
      throw new InventoryReservationException(
          "Inventory reservation failed for orderId "
              + orderId
              + " with status "
              + ex.getStatusCode(),
          ex,
          ex.getStatusCode().is4xxClientError() ? errorMessage(ex) : null);
    } catch (RestClientException ex) {
      throw new InventoryReservationException(
          "Inventory service unreachable while reserving orderId " + orderId, ex);
    }
  }

  /** The {@code message} of Inventory's error body, or null if there isn't a readable one. */
  private static String errorMessage(RestClientResponseException ex) {
    try {
      ErrorBody body = ex.getResponseBodyAs(ErrorBody.class);
      return body == null ? null : body.message();
    } catch (RuntimeException unreadable) {
      return null;
    }
  }

  private record ErrorBody(String message) {}

  ReserveResponse reserveFallback(
      Long orderId, List<ReserveRequest.Item> items, String bearerToken, Throwable ex) {
    if (ex instanceof InventoryReservationException ire) {
      throw ire;
    }
    throw new InventoryReservationException(
        "Inventory reservation circuit breaker open for orderId " + orderId, ex);
  }

  /**
   * Commits the order's reservation so Inventory's expiry sweep never hands the stock back. Throws
   * {@link InventoryReservationException} on any failure - unlike {@link #release}, a lost confirm
   * can't be swallowed, or the paid order's stock silently becomes sellable again.
   */
  @CircuitBreaker(name = "inventory-service", fallbackMethod = "confirmFallback")
  public void confirm(Long orderId, String bearerToken) {
    try {
      restClient
          .post()
          .uri("/inventory/reservations/{orderId}/confirm", orderId)
          .header(HttpHeaders.AUTHORIZATION, bearerToken)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException ex) {
      throw new InventoryReservationException(
          "Inventory confirmation failed for orderId "
              + orderId
              + " with status "
              + ex.getStatusCode(),
          ex);
    } catch (RestClientException ex) {
      throw new InventoryReservationException(
          "Inventory service unreachable while confirming orderId " + orderId, ex);
    }
  }

  void confirmFallback(Long orderId, String bearerToken, Throwable ex) {
    if (ex instanceof InventoryReservationException ire) {
      throw ire;
    }
    throw new InventoryReservationException(
        "Inventory confirmation circuit breaker open for orderId " + orderId, ex);
  }

  @CircuitBreaker(name = "inventory-service", fallbackMethod = "releaseFallback")
  /**
   * Releases the order's reservation. Throws {@link CompensationCallException} on failure - the
   * caller (a durable compensation, see CompensationService) decides whether to retry.
   */
  public void release(Long orderId, String bearerToken) {
    try {
      restClient
          .post()
          .uri("/inventory/reservations/{orderId}/release", orderId)
          .header(HttpHeaders.AUTHORIZATION, bearerToken)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException ex) {
      throw CompensationCallException.from("Inventory release for orderId " + orderId, ex);
    }
  }

  void releaseFallback(Long orderId, String bearerToken, Throwable ex) {
    if (ex instanceof CompensationCallException cce) {
      throw cce;
    }
    throw new CompensationCallException(
        "Inventory release for orderId " + orderId + " not attempted: " + ex.getMessage(),
        ex,
        true,
        null);
  }

  public record ReserveRequest(Long orderId, List<Item> items) {
    public record Item(String sku, int quantity) {}
  }

  /**
   * Inventory reserves every line in full or none at all, and prices each with the product's
   * current unit price. Orders uses those prices as the authoritative source for the order total.
   */
  public record ReserveResponse(List<LineItem> reserved) {
    public record LineItem(String sku, int quantity, BigDecimal unitPrice) {}
  }
}
