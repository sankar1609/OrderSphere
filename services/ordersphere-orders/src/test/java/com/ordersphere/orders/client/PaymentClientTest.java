package com.ordersphere.orders.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ordersphere.orders.exception.PaymentInitiationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PaymentClientTest {

  @Test
  void initiateSendsExpectedRequestAndReturnsPaymentId() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    PaymentClient client = new PaymentClient(builder);

    server
        .expect(requestTo("http://payment-service/payments"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer token"))
        .andExpect(jsonPath("$.orderId").value(1))
        .andExpect(jsonPath("$.paymentMethodId").value(5))
        .andRespond(
            withSuccess("{\"id\": 42, \"status\": \"PENDING\"}", MediaType.APPLICATION_JSON));

    Long paymentId = client.initiate(1L, 5L, new BigDecimal("20.00"), "USD", "Bearer token");

    assertThat(paymentId).isEqualTo(42L);
    server.verify();
  }

  @Test
  void initiateThrowsPaymentInitiationExceptionOnFailure() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    PaymentClient client = new PaymentClient(builder);

    server.expect(requestTo("http://payment-service/payments")).andRespond(withServerError());

    assertThatThrownBy(
            () -> client.initiate(1L, 5L, new BigDecimal("20.00"), "USD", "Bearer token"))
        .isInstanceOf(PaymentInitiationException.class);
  }

  @Test
  void getStatusReturnsParsedStatus() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    PaymentClient client = new PaymentClient(builder);

    server
        .expect(requestTo("http://payment-service/payments/42"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer token"))
        .andRespond(
            withSuccess("{\"id\": 42, \"status\": \"COMPLETED\"}", MediaType.APPLICATION_JSON));

    PaymentClient.PaymentStatus status = client.getStatus(42L, "Bearer token");

    assertThat(status).isEqualTo(PaymentClient.PaymentStatus.COMPLETED);
  }

  @Test
  void refundSendsExpectedRequestAndSwallowsFailures() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    PaymentClient client = new PaymentClient(builder);

    server
        .expect(requestTo("http://payment-service/payments/42/refund"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer token"))
        .andExpect(jsonPath("$.reason").value("Order cancelled"))
        .andRespond(withServerError());

    client.refund(42L, "Order cancelled", "Bearer token");

    server.verify();
  }

  @Test
  void initiateFallbackRethrowsPaymentInitiationExceptionUnchanged() {
    PaymentClient client = new PaymentClient(RestClient.builder());
    PaymentInitiationException original = new PaymentInitiationException("boom");

    assertThatThrownBy(
            () ->
                client.initiateFallback(
                    1L, 5L, new BigDecimal("20.00"), "USD", "Bearer token", original))
        .isSameAs(original);
  }

  @Test
  void initiateFallbackWrapsCircuitBreakerExceptionAsPaymentInitiationException() {
    PaymentClient client = new PaymentClient(RestClient.builder());

    assertThatThrownBy(
            () ->
                client.initiateFallback(
                    1L,
                    5L,
                    new BigDecimal("20.00"),
                    "USD",
                    "Bearer token",
                    new RuntimeException("circuit breaker open")))
        .isInstanceOf(PaymentInitiationException.class);
  }

  @Test
  void getStatusFallbackWrapsCircuitBreakerExceptionAsPaymentInitiationException() {
    PaymentClient client = new PaymentClient(RestClient.builder());

    assertThatThrownBy(
            () ->
                client.getStatusFallback(
                    42L, "Bearer token", new RuntimeException("circuit breaker open")))
        .isInstanceOf(PaymentInitiationException.class);
  }

  @Test
  void refundFallbackLogsAndDoesNotThrow() {
    PaymentClient client = new PaymentClient(RestClient.builder());

    client.refundFallback(
        42L, "Order cancelled", "Bearer token", new RuntimeException("circuit breaker open"));
  }
}
