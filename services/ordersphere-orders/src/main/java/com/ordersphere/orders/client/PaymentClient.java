package com.ordersphere.orders.client;

import com.ordersphere.orders.exception.PaymentInitiationException;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PaymentClient {

  private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

  private final RestClient restClient;

  public PaymentClient(RestClient.Builder loadBalancedRestClientBuilder) {
    this.restClient = loadBalancedRestClientBuilder.baseUrl("http://payment-service").build();
  }

  public Long initiate(
      Long orderId, Long paymentMethodId, BigDecimal amount, String currency, String bearerToken) {
    try {
      PaymentIdResponse response =
          restClient
              .post()
              .uri("/payments")
              .header(HttpHeaders.AUTHORIZATION, bearerToken)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new InitiatePaymentRequest(orderId, paymentMethodId, amount, currency))
              .retrieve()
              .body(PaymentIdResponse.class);
      return response.id();
    } catch (RestClientResponseException ex) {
      throw new PaymentInitiationException(
          "Payment initiation failed for orderId " + orderId + " with status " + ex.getStatusCode(),
          ex);
    } catch (RestClientException ex) {
      throw new PaymentInitiationException(
          "Payment service unreachable while initiating payment for orderId " + orderId, ex);
    }
  }

  public PaymentStatus getStatus(Long paymentId, String bearerToken) {
    try {
      PaymentStatusResponse response =
          restClient
              .get()
              .uri("/payments/{paymentId}", paymentId)
              .header(HttpHeaders.AUTHORIZATION, bearerToken)
              .retrieve()
              .body(PaymentStatusResponse.class);
      return response.status();
    } catch (RestClientException ex) {
      throw new PaymentInitiationException("Unable to fetch status for paymentId " + paymentId, ex);
    }
  }

  public void refund(Long paymentId, String reason, String bearerToken) {
    try {
      restClient
          .post()
          .uri("/payments/{paymentId}/refund", paymentId)
          .header(HttpHeaders.AUTHORIZATION, bearerToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(new RefundPaymentRequest(reason))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException ex) {
      log.warn("Failed to refund paymentId {}: {}", paymentId, ex.getMessage());
    }
  }

  public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
  }

  public record InitiatePaymentRequest(
      Long orderId, Long paymentMethodId, BigDecimal amount, String currency) {}

  public record RefundPaymentRequest(String reason) {}

  public record PaymentIdResponse(Long id) {}

  public record PaymentStatusResponse(Long id, PaymentStatus status) {}
}
