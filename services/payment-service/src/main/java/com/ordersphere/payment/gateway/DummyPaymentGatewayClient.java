package com.ordersphere.payment.gateway;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Talks to the dummy-payment-gateway's merchant API. Swap this class for a real provider's SDK. */
@Component
public class DummyPaymentGatewayClient implements PaymentGatewayClient {

  private final RestClient restClient;

  public DummyPaymentGatewayClient(
      RestClient.Builder restClientBuilder,
      @Value("${payment.gateway.base-url}") String baseUrl,
      @Value("${payment.gateway.api-key}") String apiKey) {
    this.restClient =
        restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .build();
  }

  @Override
  public CheckoutSession createCheckoutSession(CheckoutRequest request) {
    try {
      return restClient
          .post()
          .uri("/api/checkout-sessions")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(CheckoutSession.class);
    } catch (RestClientException ex) {
      throw new PaymentGatewayException(
          "Unable to create checkout session for " + request.merchantReference(), ex);
    }
  }

  @Override
  public Optional<CheckoutSession> getCheckoutSession(String sessionId) {
    try {
      return Optional.ofNullable(
          restClient
              .get()
              .uri("/api/checkout-sessions/{id}", sessionId)
              .retrieve()
              .body(CheckoutSession.class));
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return Optional.empty();
      }
      throw new PaymentGatewayException("Unable to fetch checkout session " + sessionId, ex);
    } catch (RestClientException ex) {
      throw new PaymentGatewayException("Unable to fetch checkout session " + sessionId, ex);
    }
  }

  @Override
  public Optional<String> refund(String chargeReference) {
    try {
      RefundResult result =
          restClient
              .post()
              .uri("/api/refunds")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new RefundRequest(chargeReference))
              .retrieve()
              .body(RefundResult.class);
      return Optional.ofNullable(result).map(RefundResult::refundReference);
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return Optional.empty();
      }
      throw new PaymentGatewayException("Unable to refund charge " + chargeReference, ex);
    } catch (RestClientException ex) {
      throw new PaymentGatewayException("Unable to refund charge " + chargeReference, ex);
    }
  }

  private record RefundRequest(String chargeReference) {}

  private record RefundResult(String refundReference, String status) {}
}
