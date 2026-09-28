package com.ordersphere.orders.client;

import com.ordersphere.orders.exception.PaymentInitiationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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

  @CircuitBreaker(name = "payment-service", fallbackMethod = "initiateFallback")
  public InitiatedPayment initiate(
      Long orderId, BigDecimal amount, String currency, String bearerToken) {
    try {
      InitiatedPayment response =
          restClient
              .post()
              .uri("/payments")
              .header(HttpHeaders.AUTHORIZATION, bearerToken)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new InitiatePaymentRequest(orderId, amount, currency))
              .retrieve()
              .body(InitiatedPayment.class);
      return response;
    } catch (RestClientResponseException ex) {
      throw new PaymentInitiationException(
          "Payment initiation failed for orderId " + orderId + " with status " + ex.getStatusCode(),
          ex);
    } catch (RestClientException ex) {
      throw new PaymentInitiationException(
          "Payment service unreachable while initiating payment for orderId " + orderId, ex);
    }
  }

  InitiatedPayment initiateFallback(
      Long orderId, BigDecimal amount, String currency, String bearerToken, Throwable ex) {
    if (ex instanceof PaymentInitiationException pie) {
      throw pie;
    }
    throw new PaymentInitiationException(
        "Payment circuit breaker open while initiating for orderId " + orderId, ex);
  }

  @CircuitBreaker(name = "payment-service", fallbackMethod = "getStatusFallback")
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

  PaymentStatus getStatusFallback(Long paymentId, String bearerToken, Throwable ex) {
    if (ex instanceof PaymentInitiationException pie) {
      throw pie;
    }
    throw new PaymentInitiationException(
        "Payment circuit breaker open while fetching status for paymentId " + paymentId, ex);
  }

  @CircuitBreaker(name = "payment-service", fallbackMethod = "refundFallback")
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

  void refundFallback(Long paymentId, String reason, String bearerToken, Throwable ex) {
    log.warn("Skipping refund for paymentId {}: {}", paymentId, ex.getMessage());
  }

  public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
  }

  public record InitiatePaymentRequest(Long orderId, BigDecimal amount, String currency) {}

  public record RefundPaymentRequest(String reason) {}

  /** A pending payment and the provider's hosted page where the customer pays it. */
  public record InitiatedPayment(Long id, String checkoutUrl) {}

  public record PaymentStatusResponse(Long id, PaymentStatus status) {}
}
