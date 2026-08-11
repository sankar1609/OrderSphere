package com.ordersphere.orders.client;

import com.ordersphere.orders.exception.InventoryReservationException;
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

  public void reserve(Long orderId, List<ReserveRequest.Item> items, String bearerToken) {
    try {
      restClient
          .post()
          .uri("/inventory/reservations")
          .header(HttpHeaders.AUTHORIZATION, bearerToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(new ReserveRequest(orderId, items))
          .retrieve()
          .toBodilessEntity();
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

  public record ReserveRequest(Long orderId, List<Item> items) {
    public record Item(String sku, int quantity) {}
  }
}
