package com.ordersphere.orders.client;

import com.ordersphere.orders.exception.InventoryReservationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class InventoryClient {

  private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

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
          ex);
    } catch (RestClientException ex) {
      throw new InventoryReservationException(
          "Inventory service unreachable while reserving orderId " + orderId, ex);
    }
  }

  ReserveResponse reserveFallback(
      Long orderId, List<ReserveRequest.Item> items, String bearerToken, Throwable ex) {
    if (ex instanceof InventoryReservationException ire) {
      throw ire;
    }
    throw new InventoryReservationException(
        "Inventory reservation circuit breaker open for orderId " + orderId, ex);
  }

  @CircuitBreaker(name = "inventory-service", fallbackMethod = "releaseFallback")
  public void release(Long orderId, String bearerToken) {
    try {
      restClient
          .post()
          .uri("/inventory/reservations/{orderId}/release", orderId)
          .header(HttpHeaders.AUTHORIZATION, bearerToken)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException ex) {
      log.warn(
          "Failed to release inventory reservation for orderId {}: {}", orderId, ex.getMessage());
    }
  }

  void releaseFallback(Long orderId, String bearerToken, Throwable ex) {
    log.warn("Skipping inventory release for orderId {}: {}", orderId, ex.getMessage());
  }

  public record ReserveRequest(Long orderId, List<Item> items) {
    public record Item(String sku, int quantity) {}
  }

  /**
   * Inventory prices every line, reserved or backordered, with the product's current unit price.
   * Orders uses those prices as the authoritative source for the order total.
   */
  public record ReserveResponse(List<LineItem> reserved, List<LineItem> backordered) {
    public record LineItem(String sku, int quantity, BigDecimal unitPrice) {}
  }
}
