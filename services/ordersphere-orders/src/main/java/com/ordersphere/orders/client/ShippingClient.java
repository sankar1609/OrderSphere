package com.ordersphere.orders.client;

import com.ordersphere.orders.exception.ShipmentCreationException;
import com.ordersphere.orders.exception.ShipmentLookupException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ShippingClient {

  private final RestClient restClient;

  public ShippingClient(RestClient.Builder loadBalancedRestClientBuilder) {
    this.restClient = loadBalancedRestClientBuilder.baseUrl("http://shipping-service").build();
  }

  @CircuitBreaker(name = "shipping-service", fallbackMethod = "createShipmentFallback")
  public Long createShipment(
      Long orderId, String customerUsername, String destination, String bearerToken) {
    try {
      ShipmentIdResponse response =
          restClient
              .post()
              .uri("/shipments")
              .header(HttpHeaders.AUTHORIZATION, bearerToken)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new CreateShipmentRequest(orderId, customerUsername, destination))
              .retrieve()
              .body(ShipmentIdResponse.class);
      return response.id();
    } catch (RestClientResponseException ex) {
      throw new ShipmentCreationException(
          "Shipment creation failed for orderId " + orderId + " with status " + ex.getStatusCode(),
          ex);
    } catch (RestClientException ex) {
      throw new ShipmentCreationException(
          "Shipping service unreachable while creating shipment for orderId " + orderId, ex);
    }
  }

  Long createShipmentFallback(
      Long orderId, String customerUsername, String destination, String bearerToken, Throwable ex) {
    if (ex instanceof ShipmentCreationException sce) {
      throw sce;
    }
    throw new ShipmentCreationException("Shipping circuit breaker open for orderId " + orderId, ex);
  }

  @CircuitBreaker(name = "shipping-service", fallbackMethod = "getStatusFallback")
  public ShipmentStatus getStatus(Long shipmentId, String bearerToken) {
    try {
      ShipmentStatusResponse response =
          restClient
              .get()
              .uri("/shipments/{shipmentId}", shipmentId)
              .header(HttpHeaders.AUTHORIZATION, bearerToken)
              .retrieve()
              .body(ShipmentStatusResponse.class);
      return response.status();
    } catch (RestClientException ex) {
      throw new ShipmentLookupException("Unable to fetch status for shipmentId " + shipmentId, ex);
    }
  }

  ShipmentStatus getStatusFallback(Long shipmentId, String bearerToken, Throwable ex) {
    if (ex instanceof ShipmentLookupException sle) {
      throw sle;
    }
    throw new ShipmentLookupException(
        "Shipping circuit breaker open for shipmentId " + shipmentId, ex);
  }

  public enum ShipmentStatus {
    CREATED,
    PICKED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED
  }

  public record CreateShipmentRequest(Long orderId, String customerUsername, String destination) {}

  public record ShipmentIdResponse(Long id) {}

  public record ShipmentStatusResponse(Long id, ShipmentStatus status) {}
}
